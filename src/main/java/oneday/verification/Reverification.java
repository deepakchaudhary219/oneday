package oneday.verification;

import java.time.Clock;
import java.time.Instant;

import oneday.identity.User;
import oneday.identity.UserRepository;
import oneday.identity.VerificationStatus;
import oneday.notify.Notice;
import oneday.notify.NotificationService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-verification every {@code validity} (blueprint §21.4). Accounts get one kind reminder ahead of the
 * deadline, never a stream of nags. At the deadline the verification lapses: browsing, data rights, SOS and
 * existing Date Mode safety tools keep working, but contact actions wait for a fresh liveness check, and the
 * person leaves other people's discovery until then. Idempotent; safe to run on every replica.
 */
@Component
public class Reverification {

	private final UserRepository users;

	private final NotificationService notifications;

	private final ReverificationProperties settings;

	private final Clock clock;

	public Reverification(UserRepository users, NotificationService notifications, ReverificationProperties settings,
			Clock clock) {
		this.users = users;
		this.notifications = notifications;
		this.settings = settings;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${oneday.reverification.interval:PT1H}",
			initialDelayString = "${oneday.reverification.initial-delay:PT15M}")
	public void scheduled() {
		remind();
		expire();
	}

	@Transactional
	public int remind() {
		Instant now = clock.instant();
		Instant remindFrom = now.minus(settings.validity()).plus(settings.reminderBefore());
		int reminded = 0;
		for (User user : users.findTop500ByVerificationStatusAndVerifiedAtBeforeAndReverifyRemindedAtIsNull(
				VerificationStatus.VERIFIED, remindFrom)) {
			user.reverifyReminded(now);
			notifications.notice(user.getId(), Notice.Kind.ACCOUNT, "Your quick liveness re-check is due within "
					+ settings.reminderBefore().toDays()
					+ " days. It keeps OneDay a place where everyone is who they say they are.");
			reminded++;
		}
		return reminded;
	}

	@Transactional
	public int expire() {
		Instant cutoff = clock.instant().minus(settings.validity());
		int expired = 0;
		for (User user : users.findTop500ByVerificationStatusAndVerifiedAtBefore(VerificationStatus.VERIFIED, cutoff)) {
			user.expireVerification();
			notifications.notice(user.getId(), Notice.Kind.ACCOUNT,
					"Time for your re-check. You can keep browsing; signals, chat and posting resume after a quick liveness check.");
			expired++;
		}
		return expired;
	}
}
