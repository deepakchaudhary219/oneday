package oneday.events;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A serialized {@link DomainEvent} waiting for, or done with, delivery to its consumers. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

	public enum Status {
		/** Waiting for (another) delivery attempt. */
		PENDING,
		/** Every consumer handled it. */
		DISPATCHED,
		/** Gave up after the maximum number of attempts; needs a person (see the staff console). */
		DEAD
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String eventType;

	@Column(nullable = false)
	private int schemaVersion;

	@Column(nullable = false)
	private String aggregateId;

	@Column(nullable = false)
	private String userIds;

	@Column(nullable = false)
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false)
	private Instant occurredAt;

	@Column(nullable = false)
	private Instant nextAttemptAt;

	private String lockedBy;

	private Instant lockedUntil;

	private Instant dispatchedAt;

	private String lastError;

	protected OutboxEvent() {
	}

	OutboxEvent(DomainEvent event, String payload, Instant now) {
		this.id = Ids.newId();
		this.eventType = event.type();
		this.schemaVersion = DomainEvent.SCHEMA_VERSION;
		this.aggregateId = event.aggregateId();
		this.userIds = String.join(",", event.userIds());
		this.payload = payload;
		this.status = Status.PENDING;
		this.occurredAt = now;
		this.nextAttemptAt = now;
	}

	void dispatched(Instant now) {
		status = Status.DISPATCHED;
		dispatchedAt = now;
		lockedBy = null;
		lockedUntil = null;
		lastError = null;
	}

	/** Records a failed attempt; retries back off exponentially, then the event is parked as DEAD. */
	void failed(String error, Instant now, int maxAttempts) {
		attempts++;
		lastError = error == null ? "unknown" : error.length() > 500 ? error.substring(0, 500) : error;
		lockedBy = null;
		lockedUntil = null;
		if (attempts >= maxAttempts) {
			status = Status.DEAD;
		}
		else {
			long backoffSeconds = Math.min(3600, 1L << Math.min(attempts, 12));
			nextAttemptAt = now.plusSeconds(backoffSeconds);
		}
	}

	void requeue(Instant now) {
		status = Status.PENDING;
		attempts = 0;
		nextAttemptAt = now;
	}

	public String getId() {
		return id;
	}

	public String getEventType() {
		return eventType;
	}

	public int getSchemaVersion() {
		return schemaVersion;
	}

	public String getAggregateId() {
		return aggregateId;
	}

	public String getPayload() {
		return payload;
	}

	public Status getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public Instant getDispatchedAt() {
		return dispatchedAt;
	}

	public String getLastError() {
		return lastError;
	}
}
