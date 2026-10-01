package oneday.wellbeing;

import oneday.consent.ConsentPurpose;
import oneday.consent.ConsentWithdrawalEffect;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class WellbeingConsentEffects {

	@Bean
	ConsentWithdrawalEffect wellbeingSurveyWithdrawal(WellbeingService wellbeing) {
		return ConsentWithdrawalEffect.of(ConsentPurpose.WELLBEING_SURVEY, wellbeing::withdrawAnswers);
	}
}
