package oneday.plans;

import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Plans and Rooms are blockable and reportable from where people meet them. */
@Configuration(proxyBeanMethods = false)
class PlanSafetyTargets {

	@Bean
	SafetyTargetResolver planHostTarget(PlanService plans) {
		return SafetyTargets.of("planId", "PLAN", plans::hostOf);
	}

	@Bean
	SafetyTargetResolver roomMessageTarget(PlanService plans) {
		return SafetyTargets.of("roomMessageId", "ROOM_MESSAGE", plans::senderOf);
	}
}
