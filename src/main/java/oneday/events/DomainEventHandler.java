package oneday.events;

import java.util.function.BiConsumer;

/**
 * A consumer of one event type. Delivery is at least once; the {@link EventDispatcher} makes it
 * effectively once per handler by recording {@link #name()} with the event id in the same transaction as
 * {@link #handle}. A handler that throws is retried later with the rest of the event's pending consumers.
 *
 * @param <E> the event type
 */
public interface DomainEventHandler<E extends DomainEvent> {

	Class<E> eventType();

	/** Stable consumer id (the inbox key). Renaming it makes the consumer see old events again. */
	String name();

	void handle(E event, EventMetadata metadata);

	/** A handler from a lambda, for consumers that are one method of a larger component. */
	static <E extends DomainEvent> DomainEventHandler<E> of(String name, Class<E> type,
			BiConsumer<E, EventMetadata> body) {
		return new DomainEventHandler<>() {

			@Override
			public Class<E> eventType() {
				return type;
			}

			@Override
			public String name() {
				return name;
			}

			@Override
			public void handle(E event, EventMetadata metadata) {
				body.accept(event, metadata);
			}
		};
	}
}
