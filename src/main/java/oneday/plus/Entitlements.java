package oneday.plus;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a person's plan allows. The only things Plus changes are listed here; every safety feature, every
 * approach (signals, reveals, chat) and every privacy control is identical on both plans.
 */
@Component
public class Entitlements {

	private final SubscriptionRepository subscriptions;

	private final PlusProperties settings;

	private final Clock clock;

	public Entitlements(SubscriptionRepository subscriptions, PlusProperties settings, Clock clock) {
		this.subscriptions = subscriptions;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public boolean isPlus(String userId) {
		Instant now = clock.instant();
		return subscriptions.findByUserIdOrderByCreatedAtDesc(userId)
			.stream()
			.anyMatch(s -> s.getCurrentEnd() != null && now.isBefore(s.getCurrentEnd().plus(settings.grace()))
					&& (s.getStatus() == Subscription.Status.ACTIVE || s.getStatus() == Subscription.Status.HALTED
							|| s.getStatus() == Subscription.Status.CANCELLED));
	}

	/** Discovery radius cap (the Plus "wider radius dial", blueprint v2 §7.5). */
	public int maxRadiusKm(String userId) {
		return isPlus(userId) ? settings.plusMaxRadiusKm() : settings.freeMaxRadiusKm();
	}

	public int maxOpenPlans(String userId) {
		return isPlus(userId) ? settings.plusMaxOpenPlans() : settings.freeMaxOpenPlans();
	}
}
