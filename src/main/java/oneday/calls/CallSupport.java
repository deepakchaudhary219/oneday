package oneday.calls;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;
import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CallSupport {

	/** A block ends any call between the two at once. */
	@Bean
	DomainEventHandler<DomainEvent.UserBlocked> endCallsOnBlock(CallService calls) {
		return DomainEventHandler.of("calls.end-on-block", DomainEvent.UserBlocked.class,
				(e, meta) -> calls.endBetween(e.blockerId(), e.blockedId(), Call.EndReason.BLOCKED));
	}

	/** A call can be blocked or reported from the call screen. */
	@Bean
	SafetyTargetResolver callTarget(CallService calls) {
		return SafetyTargets.of("callId", "CALL", calls::otherOn);
	}
}
