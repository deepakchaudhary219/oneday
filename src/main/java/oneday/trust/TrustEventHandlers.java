package oneday.trust;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TrustEventHandlers {

	/** A block removes vouches in both directions. */
	@Bean
	DomainEventHandler<DomainEvent.UserBlocked> removeVouchesOnBlock(VouchService vouches) {
		return DomainEventHandler.of("trust.unvouch-on-block", DomainEvent.UserBlocked.class,
				(e, meta) -> vouches.removeBetween(e.blockerId(), e.blockedId()));
	}
}
