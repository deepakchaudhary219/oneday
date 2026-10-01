package oneday.events;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Calls every handler of the event's type, each in its own transaction together with its inbox row
 * (idempotent consumer). A handler that already processed the event is skipped, so a retry after a partial
 * failure only re-runs the consumers that failed.
 */
@Component
class InProcessEventTransport implements EventTransport {

	private static final Logger log = LoggerFactory.getLogger(InProcessEventTransport.class);

	private final List<DomainEventHandler<?>> handlers;

	private final ProcessedEventRepository inbox;

	private final TransactionTemplate transactions;

	private final Clock clock;

	InProcessEventTransport(List<DomainEventHandler<?>> handlers, ProcessedEventRepository inbox,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.handlers = List.copyOf(handlers);
		this.inbox = inbox;
		this.transactions = new TransactionTemplate(transactionManager);
		this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
		long names = this.handlers.stream().map(DomainEventHandler::name).distinct().count();
		if (names != this.handlers.size()) {
			throw new IllegalStateException("Event handler names must be unique");
		}
	}

	@Override
	public void deliver(DomainEvent event, EventMetadata metadata) {
		RuntimeException failure = null;
		for (DomainEventHandler<?> handler : handlers) {
			if (!handler.eventType().isInstance(event)) {
				continue;
			}
			try {
				transactions.executeWithoutResult(status -> handleOnce(handler, event, metadata));
			}
			catch (RuntimeException ex) {
				log.warn("Event handler {} failed on {} {}", handler.name(), event.type(), metadata.eventId(), ex);
				if (failure == null) {
					failure = ex;
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	private <E extends DomainEvent> void handleOnce(DomainEventHandler<E> handler, DomainEvent event,
			EventMetadata metadata) {
		ProcessedEvent.Key key = new ProcessedEvent.Key(handler.name(), metadata.eventId());
		if (inbox.existsById(key)) {
			return;
		}
		handler.handle(handler.eventType().cast(event), metadata);
		// saveAndFlush: a concurrent duplicate fails here on the primary key and rolls the handler back with it.
		inbox.saveAndFlush(new ProcessedEvent(handler.name(), metadata.eventId(), clock.instant()));
	}
}
