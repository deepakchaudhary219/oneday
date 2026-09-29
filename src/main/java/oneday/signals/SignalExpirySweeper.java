package oneday.signals;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Quietly archives signals whose Reaction Window closed. Reads already filter by window, so correctness
 * never depends on this job; it only keeps the pending set small. No one is notified.
 */
@Component
class SignalExpirySweeper {

	private final SignalService signals;

	SignalExpirySweeper(SignalService signals) {
		this.signals = signals;
	}

	@Scheduled(fixedDelayString = "${oneday.signals.sweep-interval:PT15M}", initialDelayString = "PT1M")
	void sweep() {
		signals.archiveExpired();
	}
}
