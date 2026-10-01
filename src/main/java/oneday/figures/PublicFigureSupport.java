package oneday.figures;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;
import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PublicFigureSupport {

	/** A block ends following in both directions. */
	@Bean
	DomainEventHandler<DomainEvent.UserBlocked> unfollowOnBlock(PublicFigureService figures) {
		return DomainEventHandler.of("figures.unfollow-on-block", DomainEvent.UserBlocked.class,
				(e, meta) -> figures.unfollowBetween(e.blockerId(), e.blockedId()));
	}

	/** A figure can be blocked or reported from their profile. */
	@Bean
	SafetyTargetResolver figureTarget(PublicFigureService figures) {
		return SafetyTargets.of("figureHandle", "PUBLIC_FIGURE", figures::figureBehind);
	}
}
