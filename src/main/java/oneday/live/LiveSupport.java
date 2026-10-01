package oneday.live;

import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class LiveSupport {

	@Bean
	SafetyTargetResolver liveTarget(LiveService live) {
		return SafetyTargets.of("liveId", "LIVE", live::hostOf);
	}
}
