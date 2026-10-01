package oneday.pulsestatus;

import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A status can be blocked or reported from where friends see it. */
@Configuration(proxyBeanMethods = false)
class PulseStatusSafetyTargets {

	@Bean
	SafetyTargetResolver pulseStatusTarget(PulseStatusService statuses) {
		return SafetyTargets.of("pulseStatusId", "PULSE_STATUS", statuses::ownerOf);
	}
}
