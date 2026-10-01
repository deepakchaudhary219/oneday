package oneday.pulsestatus;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes expired statuses. Reads already filter by expiry, so this only keeps the table small. */
@Component
class PulseStatusSweeper {

	private final PulseStatusService statuses;

	PulseStatusSweeper(PulseStatusService statuses) {
		this.statuses = statuses;
	}

	@Scheduled(fixedDelayString = "${oneday.pulse-status.sweep-interval:PT15M}", initialDelayString = "PT2M")
	void sweep() {
		statuses.purgeExpired();
	}
}
