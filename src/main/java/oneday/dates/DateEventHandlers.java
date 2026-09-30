package oneday.dates;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;
import oneday.profile.Profile;
import oneday.profile.ProfileService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Date Mode's consumers of domain events, delivered through the outbox (at least once, idempotent). */
@Configuration(proxyBeanMethods = false)
class DateEventHandlers {

	/**
	 * Tells the person's trusted contact they may need help. Going through the outbox means the text is sent
	 * if and only if the escalation committed, and a failed send is retried. The contact already holds the
	 * live link from when they were added, so the alert itself carries no location.
	 */
	@Bean
	DomainEventHandler<DomainEvent.DateSafetyEscalated> alertTrustedContact(DateService dates,
			TrustedContactMessenger sms, ProfileService profiles, DateProperties settings) {
		return DomainEventHandler.of("dates.alert-trusted-contact", DomainEvent.DateSafetyEscalated.class, (e, meta) -> {
			DateParticipant who = dates.participantOf(e.dateId(), e.userId()).orElse(null);
			DatePlan plan = dates.find(e.dateId()).orElse(null);
			if (who == null || plan == null || who.getContactPhone() == null) {
				return;
			}
			String name = profiles.find(e.userId()).map(Profile::firstName).orElse("Your friend");
			String what = EscalationReason.MISSED_CHECK_IN.name().equals(e.reason()) ? "didn't answer a check-in"
					: "asked for help";
			sms.textOrThrow(who.getContactPhone(), "OneDay safety alert: " + name + " " + what
					+ " during their meet-up at " + plan.getPlaceName()
					+ ". Try calling them now; the link we sent you earlier shows where they are if they're sharing."
					+ " If you can't reach them and are worried, call " + settings.emergencyNumber() + ".");
		});
	}

	/** A block cancels every open plan between the two people (and stops any exact-location sharing). */
	@Bean
	DomainEventHandler<DomainEvent.UserBlocked> cancelPlansOnBlock(DateService dates) {
		return DomainEventHandler.of("dates.cancel-on-block", DomainEvent.UserBlocked.class,
				(e, meta) -> dates.cancelBetween(e.blockerId(), e.blockedId()));
	}
}
