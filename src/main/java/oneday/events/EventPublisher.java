package oneday.events;

import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox: {@link #publish} stores the event in the caller's transaction, so the event exists
 * if and only if the state change that caused it committed. No dual write, no lost or phantom events; the
 * {@link OutboxRelay} delivers it afterwards. {@link Propagation#MANDATORY} makes publishing outside a
 * transaction a programming error rather than a silent consistency bug.
 */
@Component
public class EventPublisher {

	private static final Map<String, Class<? extends DomainEvent>> TYPES = Arrays
		.stream(DomainEvent.class.getPermittedSubclasses())
		.map(c -> c.asSubclass(DomainEvent.class))
		.collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));

	private final OutboxRepository outbox;

	private final JsonMapper json;

	private final Clock clock;

	public EventPublisher(OutboxRepository outbox, JsonMapper json, Clock clock) {
		this.outbox = outbox;
		this.json = json;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void publish(DomainEvent event) {
		outbox.save(new OutboxEvent(event, json.writeValueAsString(event), clock.instant()));
	}

	DomainEvent read(OutboxEvent stored) {
		Class<? extends DomainEvent> type = TYPES.get(stored.getEventType());
		if (type == null) {
			throw new IllegalStateException("Unknown event type " + stored.getEventType());
		}
		return json.readValue(stored.getPayload(), type);
	}
}
