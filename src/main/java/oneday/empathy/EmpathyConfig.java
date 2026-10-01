package oneday.empathy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
class EmpathyConfig {

	@Bean
	@ConditionalOnMissingBean
	ToneClassifier lexiconToneClassifier(@Value("${oneday.empathy.lexicon:classpath:empathy/lexicon.txt}") Resource lexicon) {
		return new LexiconToneClassifier(lexicon);
	}
}
