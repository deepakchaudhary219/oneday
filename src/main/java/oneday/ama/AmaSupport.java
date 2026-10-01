package oneday.ama;

import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AmaSupport {

	@Bean
	SafetyTargetResolver amaQuestionTarget(AmaService amas) {
		return SafetyTargets.of("amaQuestionId", "AMA_QUESTION", amas::askerOf);
	}
}
