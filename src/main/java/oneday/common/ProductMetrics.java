package oneday.common;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * The product's own counters, next to the JVM, HTTP and database metrics Spring Boot records. They measure
 * the core loop (moments, signals, reveals) and safety health (reports, sign-in abuse, media, grievances).
 * Tags are small fixed vocabularies: never a user id, a place or free text.
 */
@Component
public class ProductMetrics {

	private final MeterRegistry registry;

	public ProductMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void momentPublished(Enum<?> kind) {
		registry.counter("oneday.moments.published", "kind", kind.name()).increment();
	}

	public void signalSent() {
		registry.counter("oneday.signals.sent").increment();
	}

	/** A Mutual Reveal: two people connected. The number the product exists to grow. */
	public void mutualReveal() {
		registry.counter("oneday.reveals").increment();
	}

	public void reportFiled(Enum<?> priority) {
		registry.counter("oneday.reports.filed", "priority", priority.name()).increment();
	}

	/** {@code outcome}: success, failure or rate_limited. */
	public void login(String outcome) {
		registry.counter("oneday.auth.logins", "outcome", outcome).increment();
	}

	/** A replayed refresh token: a possible account takeover, worth an alert when it climbs. */
	public void refreshTokenReused() {
		registry.counter("oneday.sessions.reuse_detected").increment();
	}

	/** {@code outcome}: ready, rejected or failed (retried). */
	public void mediaProcessed(String outcome) {
		registry.counter("oneday.media.processed", "outcome", outcome).increment();
	}

	public void grievanceFiled(Enum<?> category) {
		registry.counter("oneday.grievances.filed", "category", category.name()).increment();
	}
}
