package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;
import oneday.events.EventPublisher;
import oneday.events.OutboxRepository;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The transactional outbox: events commit with their state change, are delivered at least once, consumers
 * are idempotent, failures back off and end in a dead-letter queue that staff can requeue.
 */
@Import(OutboxIntegrationTest.FlakyConsumer.class)
@TestPropertySource(properties = "oneday.events.max-attempts=3")
class OutboxIntegrationTest extends ApiTestSupport {

	static final AtomicBoolean FAIL = new AtomicBoolean();

	@Autowired
	private EventPublisher publisher;

	@Autowired
	private OutboxRepository outbox;

	@Autowired
	private MeterRegistry metrics;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void theCoreLoopEmitsEventsThatBuildTheLedgerExactlyOnce() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		updateProfile(asha, "{\"homeRegion\":\"IN-KL\"}");
		updateProfile(ravi, "{\"homeRegion\":\"in-kl\"}");
		connect(asha, ravi);

		assertThat(jdbc.queryForList("select event_type from outbox_events where status = 'PENDING' order by id",
				String.class))
			.containsExactly("MomentPublished", "SignalSent", "MutualRevealed");
		// The read model is eventually consistent: nothing until the relay runs.
		getAs(asha, "/ledger").andExpect(jsonPath("$.newConnections").value(0));
		assertThat(metrics.get("oneday.outbox.pending").gauge().value()).isEqualTo(3.0);

		assertThat(deliverEvents()).isEqualTo(3);
		assertThat(countByStatus("DISPATCHED")).isEqualTo(3);
		getAs(asha, "/ledger").andExpect(status().isOk())
			.andExpect(jsonPath("$.newConnections").value(1))
			.andExpect(jsonPath("$.rootsConnections").value(1))
			.andExpect(jsonPath("$.lines[0]").value("1 new connection (1 from your home region)"));
		getAs(ravi, "/ledger").andExpect(jsonPath("$.newConnections").value(1));

		// Redelivery (a relay crashed after handling, before marking) changes nothing: the inbox dedupes...
		jdbc.update("update outbox_events set status = 'PENDING', dispatched_at = null");
		assertThat(deliverEvents()).isEqualTo(3);
		// ...and even without the inbox the projection's own key keeps it exactly-once.
		jdbc.update("delete from processed_events");
		jdbc.update("update outbox_events set status = 'PENDING', dispatched_at = null");
		deliverEvents();
		getAs(asha, "/ledger").andExpect(jsonPath("$.newConnections").value(1))
			.andExpect(jsonPath("$.rootsConnections").value(1));
		getAs(asha, "/ledger?month=1999-01").andExpect(jsonPath("$.newConnections").value(0))
			.andExpect(jsonPath("$.lines[0]", containsString("Nothing yet")));
		getAs(asha, "/ledger?month=soon").andExpect(status().isBadRequest());

		// The ledger is part of the data export; erasure removes it and every event that mentions the account.
		getAs(asha, "/privacy/export").andExpect(jsonPath("$.realValueLedger", hasSize(2)));
		deleteAs(asha, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from outbox_events where user_ids like ?", Integer.class,
				"%" + userIdOf(asha) + "%")).isZero();
		assertThat(jdbc.queryForObject("select count(*) from ledger_entries where user_id = ?", Integer.class,
				userIdOf(asha))).isZero();
	}

	@Test
	void aFailingConsumerBacksOffThenDeadLettersAndCanBeRequeued() throws Exception {
		String asha = verifiedUser("Asha");
		locate(asha, BLR_LAT, BLR_LON);
		FAIL.set(true);
		try {
			postPublicMoment(asha, "trek");
			assertThat(deliverEvents()).isZero();
			assertThat(jdbc.queryForObject("select attempts from outbox_events", Integer.class)).isEqualTo(1);
			// Backing off: not due again yet.
			assertThat(deliverEvents()).isZero();
			assertThat(jdbc.queryForObject("select attempts from outbox_events", Integer.class)).isEqualTo(1);
			clock.advance(Duration.ofMinutes(5));
			deliverEvents();
			clock.advance(Duration.ofMinutes(5));
			deliverEvents();
			assertThat(countByStatus("DEAD")).isEqualTo(1);
			assertThat(metrics.get("oneday.outbox.dead").gauge().value()).isEqualTo(1.0);
		}
		finally {
			FAIL.set(false);
		}
		String admin = promote(verifiedUser("Admin"), "ADMIN");
		String dead = body(getAs(admin, "/staff/events/dead").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].type").value("MomentPublished"))
			.andExpect(jsonPath("$[0].attempts").value(3))
			.andExpect(jsonPath("$[0].lastError", containsString("consumer down"))));
		assertThat(dead).doesNotContain(userIdOf(asha));
		getAs(asha, "/staff/events/dead").andExpect(status().isForbidden());

		String eventId = JsonPath.read(dead, "$[0].id");
		postAs(admin, "/staff/events/" + eventId + "/requeue", null).andExpect(status().isOk());
		assertThat(deliverEvents()).isEqualTo(1);
		assertThat(countByStatus("DISPATCHED")).isEqualTo(1);
		getAs(admin, "/staff/audit").andExpect(jsonPath("$[0].action").value("EVENT_REQUEUED"));
	}

	@Test
	void publishingOutsideATransactionIsRefusedAndLeasesAreExclusive() throws Exception {
		assertThatThrownBy(() -> publisher.publish(new DomainEvent.UserBlocked("a", "b")))
			.isInstanceOf(IllegalTransactionStateException.class);

		String asha = verifiedUser("Asha");
		locate(asha, BLR_LAT, BLR_LON);
		postPublicMoment(asha, "trek");
		String id = jdbc.queryForObject("select id from outbox_events", String.class);
		Instant now = clock.instant();
		// Two replicas race for the same event: exactly one gets the lease until it runs out.
		assertThat(claim(id, "replica-a", now, now.plusSeconds(30))).isEqualTo(1);
		assertThat(claim(id, "replica-b", now, now.plusSeconds(30))).isZero();
		assertThat(claim(id, "replica-b", now.plusSeconds(31), now.plusSeconds(61))).isEqualTo(1);
	}

	private int claim(String id, String node, Instant now, Instant until) {
		return new TransactionTemplate(transactionManager).execute(s -> outbox.claim(id, node, now, until));
	}

	private int countByStatus(String status) {
		return jdbc.queryForObject("select count(*) from outbox_events where status = ?", Integer.class, status);
	}

	/** A consumer that can be switched off, to exercise retries and the dead-letter queue. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FlakyConsumer {

		@Bean
		DomainEventHandler<DomainEvent.MomentPublished> flakyConsumer() {
			return DomainEventHandler.of("test.flaky", DomainEvent.MomentPublished.class, (e, meta) -> {
				if (FAIL.get()) {
					throw new IllegalStateException("consumer down");
				}
			});
		}
	}
}
