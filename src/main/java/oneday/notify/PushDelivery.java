package oneday.notify;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Delivers queued pushes after their transaction committed; provider failures are retried by the outbox. */
@Configuration(proxyBeanMethods = false)
class PushDelivery {

	@Bean
	DomainEventHandler<DomainEvent.PushRequested> deliverPush(NotificationService notifications) {
		return DomainEventHandler.of("notify.push", DomainEvent.PushRequested.class,
				(e, meta) -> notifications.deliverNow(e.userId(), e.title(), e.body(), e.data()));
	}
}
