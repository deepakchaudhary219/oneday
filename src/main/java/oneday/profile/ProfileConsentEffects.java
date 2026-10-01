package oneday.profile;

import oneday.consent.ConsentPurpose;
import oneday.consent.ConsentWithdrawalEffect;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The profile fields each purpose covers are deleted when its consent is withdrawn. */
@Configuration(proxyBeanMethods = false)
class ProfileConsentEffects {

	@Bean
	ConsentWithdrawalEffect datingPreferencesWithdrawal(ProfileService profiles) {
		return ConsentWithdrawalEffect.of(ConsentPurpose.DATING_PREFERENCES, profiles::clearDatingPreferences);
	}

	@Bean
	ConsentWithdrawalEffect rootsAndLanguagesWithdrawal(ProfileService profiles) {
		return ConsentWithdrawalEffect.of(ConsentPurpose.ROOTS_AND_LANGUAGES, profiles::clearRootsAndLanguages);
	}
}
