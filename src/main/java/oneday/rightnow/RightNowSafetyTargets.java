package oneday.rightnow;

import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RightNowSafetyTargets {

	@Bean
	SafetyTargetResolver rightNowTarget(RightNowService rightNow) {
		return SafetyTargets.of("rightNowId", "RIGHT_NOW", (viewer, id) -> rightNow.ownerOf(id));
	}
}
