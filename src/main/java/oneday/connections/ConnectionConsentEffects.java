package oneday.connections;

import oneday.consent.ConsentPurpose;
import oneday.consent.ConsentWithdrawalEffect;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Sparks and Couple Mode are romantic-interest data, covered by the dating-preferences consent. */
@Configuration(proxyBeanMethods = false)
class ConnectionConsentEffects {

	@Bean
	ConsentWithdrawalEffect sparksWithdrawal(ConnectionService connections) {
		return ConsentWithdrawalEffect.of(ConsentPurpose.DATING_PREFERENCES, connections::withdrawSparks);
	}
}
