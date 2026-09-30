package oneday.events;

import java.time.Instant;

/** Envelope fields a consumer may need: the event id (for idempotency keys) and when the fact happened. */
public record EventMetadata(String eventId, Instant occurredAt, int attempt) {
}
