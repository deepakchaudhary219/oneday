package oneday.dates;

import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DateSafetyTargets {

	@Bean
	SafetyTargetResolver dateTarget(DateService dates) {
		return SafetyTargets.of("dateId", "DATE", dates::partnerOf);
	}
}
