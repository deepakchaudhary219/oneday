package oneday.geo;

import oneday.consent.ConsentPurpose;
import oneday.consent.ConsentWithdrawalEffect;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Withdrawing location consent deletes the stored cell, which takes the person out of every nearby surface. */
@Configuration(proxyBeanMethods = false)
class LocationConsentEffects {

	@Bean
	ConsentWithdrawalEffect locationDiscoveryWithdrawal(LocationService locations) {
		return ConsentWithdrawalEffect.of(ConsentPurpose.LOCATION_DISCOVERY, locations::withdraw);
	}
}
