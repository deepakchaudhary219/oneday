package oneday.events;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves committed events from the outbox to their consumers. Every replica runs a relay; a short lease per
 * event keeps two relays off the same event, and a relay that dies mid-delivery only delays its events until
 * the lease runs out. Delivery is at least once and not ordered across aggregates; consumers are idempotent
 * (see {@link EventDispatcher}).
 */
@Component
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	/** Upper bound on batches per pass, so one pass can't starve the scheduler thread. */
	private static final int MAX_BATCHES_PER_PASS = 20;

	private final OutboxRepository outbox;

	private final ProcessedEventRepository inbox;

	private final EventPublisher codec;

	private final EventTransport transport;

	private final TransactionTemplate transactions;

	private final EventsProperties settings;

	private final MeterRegistry metrics;

	private final Clock clock;

	private final String node;

	public OutboxRelay(OutboxRepository outbox, ProcessedEventRepository inbox, EventPublisher codec,
			EventTransport transport, PlatformTransactionManager transactionManager, EventsProperties settings,
			MeterRegistry metrics, Clock clock) {
		this.outbox = outbox;
		this.inbox = inbox;
		this.codec = codec;
		this.transport = transport;
		this.transactions = new TransactionTemplate(transactionManager);
		this.settings = settings;
		this.metrics = metrics;
		this.clock = clock;
		this.node = hostName() + "/" + UUID.randomUUID().toString().substring(0, 8);
	}

	@Scheduled(fixedDelayString = "${oneday.events.relay-interval:PT2S}",
			initialDelayString = "${oneday.events.relay-initial-delay:PT15S}")
	public void poll() {
		try {
			drain();
		}
		catch (RuntimeException ex) {
			log.error("Outbox relay pass failed", ex);
		}
	}

	/** Delivers everything that is due now. Returns the number of events delivered successfully. */
	public int drain() {
		int delivered = 0;
		for (int batch = 0; batch < MAX_BATCHES_PER_PASS; batch++) {
			List<String> claimed = claimBatch();
			if (claimed.isEmpty()) {
				break;
			}
			for (String id : claimed) {
				if (deliver(id)) {
					delivered++;
				}
			}
		}
		return delivered;
	}

	private List<String> claimBatch() {
		return transactions.execute(status -> {
			Instant now = clock.instant();
			Instant until = now.plus(settings.lease());
			return outbox.findDue(now, PageRequest.of(0, settings.batchSize()))
				.stream()
				.filter(id -> outbox.claim(id, node, now, until) == 1)
				.toList();
		});
	}

	private boolean deliver(String id) {
		OutboxEvent stored = outbox.findById(id).orElse(null);
		if (stored == null) {
			return false; // erased while in flight
		}
		try {
			DomainEvent event = codec.read(stored);
			transport.deliver(event, new EventMetadata(stored.getId(), stored.getOccurredAt(), stored.getAttempts() + 1));
			finish(id, null);
			metrics.counter("oneday.events.dispatched", "type", stored.getEventType()).increment();
			return true;
		}
		catch (RuntimeException ex) {
			finish(id, ex.getClass().getSimpleName() + ": " + ex.getMessage());
			metrics.counter("oneday.events.failed", "type", stored.getEventType()).increment();
			return false;
		}
	}

	private void finish(String id, String error) {
		transactions.executeWithoutResult(status -> outbox.findById(id).ifPresent(e -> {
			if (error == null) {
				e.dispatched(clock.instant());
			}
			else {
				e.failed(error, clock.instant(), settings.maxAttempts());
				if (e.getStatus() == OutboxEvent.Status.DEAD) {
					log.error("Event {} {} parked as DEAD after {} attempts: {}", e.getEventType(), id,
							e.getAttempts(), error);
				}
			}
		}));
	}

	/** Delivered events and inbox rows are only needed for a while (dedup window, debugging). */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT20M")
	public void purge() {
		Instant before = clock.instant().minus(settings.retention());
		transactions.executeWithoutResult(status -> {
			outbox.deleteDispatchedBefore(before);
			inbox.deleteProcessedBefore(before);
		});
	}

	private static String hostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		}
		catch (Exception ex) {
			return "node";
		}
	}
}
