package oneday.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The default transport: the relay hands each event straight to the consumers in this process. */
@Component
@ConditionalOnProperty(name = "oneday.events.transport", havingValue = "in-process", matchIfMissing = true)
class InProcessEventTransport implements EventTransport {

	private final EventDispatcher dispatcher;

	InProcessEventTransport(EventDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Override
	public void deliver(DomainEvent event, EventMetadata metadata) {
		dispatcher.dispatch(event, metadata);
	}
}
