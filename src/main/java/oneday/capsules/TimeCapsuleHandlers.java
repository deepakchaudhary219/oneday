package oneday.capsules;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeCapsuleHandlers {

	/** Copies a capsule's media out of the expiring story prefix; retried by the outbox if storage is down. */
	@Bean
	DomainEventHandler<DomainEvent.CapsuleSealed> preserveCapsuleMedia(TimeCapsuleService capsules) {
		return DomainEventHandler.of("capsules.preserve-media", DomainEvent.CapsuleSealed.class,
				(e, meta) -> capsules.preserveMedia(e.capsuleId()));
	}

	/** A block cancels sealed capsules between the two, both ways. */
	@Bean
	DomainEventHandler<DomainEvent.UserBlocked> cancelCapsulesOnBlock(TimeCapsuleService capsules) {
		return DomainEventHandler.of("capsules.cancel-on-block", DomainEvent.UserBlocked.class,
				(e, meta) -> capsules.cancelBetween(e.blockerId(), e.blockedId()));
	}
}
