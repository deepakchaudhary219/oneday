package oneday.events;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import oneday.common.ApiException;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operating the outbox: lag and dead-letter gauges to alert on, the dead-letter queue for staff, and
 * erasure. Callers enforce staff roles.
 */
@Component
public class EventOperations implements MeterBinder {

	private final OutboxRepository outbox;

	private final Clock clock;

	public EventOperations(OutboxRepository outbox, Clock clock) {
		this.outbox = outbox;
		this.clock = clock;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder("oneday.outbox.pending", () -> outbox.countByStatus(OutboxEvent.Status.PENDING))
			.description("Events waiting for delivery")
			.register(registry);
		Gauge.builder("oneday.outbox.dead", () -> outbox.countByStatus(OutboxEvent.Status.DEAD))
			.description("Events that exhausted their retries; each needs a person")
			.register(registry);
		Gauge.builder("oneday.outbox.lag.seconds", this::lagSeconds)
			.description("Age of the oldest undelivered event")
			.register(registry);
	}

	double lagSeconds() {
		return outbox.findFirstByStatusOrderByOccurredAtAsc(OutboxEvent.Status.PENDING)
			.map(e -> (double) Duration.between(e.getOccurredAt(), clock.instant()).toSeconds())
			.orElse(0.0);
	}

	@Transactional(readOnly = true)
	public List<DeadEvent> deadLetters(int limit) {
		return outbox.findByStatusOrderByOccurredAtDesc(OutboxEvent.Status.DEAD, PageRequest.of(0, Math.clamp(limit, 1, 200)))
			.stream()
			.map(e -> new DeadEvent(e.getId(), e.getEventType(), e.getAggregateId(), e.getAttempts(), e.getOccurredAt(),
					e.getLastError()))
			.toList();
	}

	/** Puts a DEAD event back in the queue once its cause is fixed. Consumers that succeeded are not re-run. */
	@Transactional
	public DeadEvent requeue(String eventId) {
		OutboxEvent event = outbox.findById(eventId)
			.filter(e -> e.getStatus() == OutboxEvent.Status.DEAD)
			.orElseThrow(() -> ApiException.notFound("Dead event"));
		event.requeue(clock.instant());
		return new DeadEvent(event.getId(), event.getEventType(), event.getAggregateId(), 0, event.getOccurredAt(),
				event.getLastError());
	}

	@Transactional(readOnly = true)
	public long pending() {
		return outbox.countByStatus(OutboxEvent.Status.PENDING);
	}

	/** Erasure: drop every event that mentions the account. */
	@Transactional
	public void forget(String userId) {
		outbox.deleteMentioning(userId);
	}

	/** Payloads are not shown: they hold account ids, and the type and aggregate are enough to investigate. */
	public record DeadEvent(String id, String type, String aggregateId, int attempts, Instant occurredAt,
			String lastError) {
	}
}
