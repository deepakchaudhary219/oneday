package oneday.dates;

import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Date Mode's clock: "Going OK?" prompts, escalation of unanswered ones, closing plans whose time box is
 * over, and purging trusted-contact details afterwards. Each step is idempotent and safe on every replica
 * (plans carry an optimistic lock, so two replicas cannot both close one).
 */
@Component
class DateModeScheduler {

	private static final Logger log = LoggerFactory.getLogger(DateModeScheduler.class);

	private final DateService dates;

	DateModeScheduler(DateService dates) {
		this.dates = dates;
	}

	@Scheduled(fixedDelayString = "${oneday.dates.sweep-interval:PT1M}",
			initialDelayString = "${oneday.dates.sweep-initial-delay:PT30S}")
	void tick() {
		run("check-in prompts", dates::promptDueCheckIns);
		run("missed check-ins", dates::escalateMissedCheckIns);
		run("closing plans", dates::closeFinishedPlans);
		run("purging plans", dates::purgeClosedPlans);
	}

	private static void run(String step, IntSupplier work) {
		try {
			work.getAsInt();
		}
		catch (RuntimeException ex) {
			// Another replica won an optimistic-lock race, or a transient failure: the next tick retries.
			log.warn("Date Mode step '{}' failed; retrying next tick", step, ex);
		}
	}
}
