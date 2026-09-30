package oneday.events;

/**
 * Where the relay hands events. In the monolith that is {@link InProcessEventTransport}, which calls the
 * handlers directly. When a Kafka trigger fires (tech arch v2 §1.3), a broker transport replaces it and the
 * handlers become consumer-group members; neither the publishers nor the outbox change.
 */
public interface EventTransport {

	/** Returns normally only when every consumer has handled the event. */
	void deliver(DomainEvent event, EventMetadata metadata);
}
