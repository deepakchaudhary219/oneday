package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The Kafka transport end to end against an embedded broker: outbox → relay → topic (keyed by aggregate) →
 * consumer group → the same idempotent handlers. A redelivered record changes nothing.
 */
@ActiveProfiles({ "test", "kafka" })
@EmbeddedKafka(partitions = 3)
@TestPropertySource(properties = { "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"oneday.events.kafka.partitions=3", "oneday.events.kafka.concurrency=1",
		"oneday.events.kafka.topic=oneday.domain-events.test" })
class KafkaTransportIntegrationTest extends ApiTestSupport {

	@Test
	void eventsFlowThroughKafkaToIdempotentConsumers() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		connect(asha, ravi);

		// The relay publishes to Kafka and marks events dispatched only once the broker acknowledged them.
		assertThat(deliverEvents()).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from outbox_events where status = 'DISPATCHED'", Integer.class))
			.isEqualTo(3);

		// The consumer group builds the ledger asynchronously.
		await().atMost(Duration.ofSeconds(30))
			.until(() -> jdbc.queryForObject("select count(*) from ledger_entries where kind = 'NEW_CONNECTION'",
					Integer.class) == 2);

		// Re-publishing the same events (a relay crash after send, before marking) is harmless.
		jdbc.update("update outbox_events set status = 'PENDING', dispatched_at = null");
		assertThat(deliverEvents()).isEqualTo(3);
		await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
			.until(() -> jdbc.queryForObject("select count(*) from processed_events", Integer.class) >= 2);
		assertThat(jdbc.queryForObject("select count(*) from ledger_entries", Integer.class)).isEqualTo(2);
	}
}
