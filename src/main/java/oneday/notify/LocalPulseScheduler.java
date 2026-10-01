package oneday.notify;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import oneday.common.ApiException;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.pulse.PulseService;
import oneday.pulse.PulseService.PulseView;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers the Local Pulse (blueprint §33.2): one honest notification a day, during the hour the user
 * chose in their own time zone. Never randomised, never repeated the same day, and never sent when there
 * is nothing real to say. The claim row makes this safe with several replicas running the same job.
 */
@Component
public class LocalPulseScheduler {

	private final DeviceRepository devices;

	private final PulseDeliveryRepository deliveries;

	private final ProfileService profiles;

	private final PulseService pulse;

	private final NotificationService notifications;

	private final TransactionTemplate claimTx;

	private final Clock clock;

	public LocalPulseScheduler(DeviceRepository devices, PulseDeliveryRepository deliveries, ProfileService profiles,
			PulseService pulse, NotificationService notifications, PlatformTransactionManager transactions,
			Clock clock) {
		this.devices = devices;
		this.deliveries = deliveries;
		this.profiles = profiles;
		this.pulse = pulse;
		this.notifications = notifications;
		this.claimTx = new TransactionTemplate(transactions);
		this.claimTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	@Scheduled(cron = "${oneday.pulse.cron:0 */10 * * * *}")
	public void run() {
		deliverDue();
	}

	/**
	 * Only people whose chosen hour is now, in their own zone, are looked at: one indexed query per time zone
	 * in use (a handful, not one per user), so the job's cost tracks the pulses due, not the user base.
	 *
	 * @return how many pulses were pushed in this pass
	 */
	public int deliverDue() {
		int sent = 0;
		for (String zone : profiles.timeZonesInUse()) {
			int hour;
			try {
				hour = clock.instant().atZone(ZoneId.of(zone)).getHour();
			}
			catch (DateTimeException ex) {
				continue;
			}
			for (String userId : devices.findUserIdsDueInZone(zone, hour)) {
				if (deliverIfDue(userId)) {
					sent++;
				}
			}
		}
		return sent;
	}

	private boolean deliverIfDue(String userId) {
		Optional<Profile> profile = profiles.find(userId);
		if (profile.isEmpty()) {
			return false;
		}
		ZonedDateTime local = clock.instant().atZone(ZoneId.of(profile.get().getTimeZone()));
		if (local.getHour() != profile.get().getPulseHour() || !claim(userId, local.toLocalDate())) {
			return false;
		}
		PulseView view;
		try {
			view = pulse.pulse(userId);
		}
		catch (ApiException inactiveAccount) {
			return false;
		}
		boolean worthSaying = !"0".equals(view.pendingSignals()) || view.newConnectionsThisWeek() > 0
				|| !view.nearbyActivity().isEmpty() || !"0".equals(view.relayAnswers())
				|| (!view.promptAnsweredByYou() && !"0".equals(view.promptAnsweredNearby()));
		if (!worthSaying) {
			return false;
		}
		int delivered = notifications.deliverNow(userId, "Your Local Pulse", view.headline(), Map.of("open", "pulse"));
		claimTx.executeWithoutResult(s -> deliveries.findById(new PulseDelivery.Key(userId, local.toLocalDate()))
			.ifPresent(PulseDelivery::markSent));
		return delivered > 0;
	}

	/** Inserts today's claim row; a duplicate key means another pass or replica already handled today. */
	private boolean claim(String userId, LocalDate localDate) {
		try {
			claimTx.executeWithoutResult(s -> deliveries
				.saveAndFlush(new PulseDelivery(userId, localDate, clock.instant())));
			return true;
		}
		catch (DataIntegrityViolationException alreadyClaimed) {
			return false;
		}
	}
}
