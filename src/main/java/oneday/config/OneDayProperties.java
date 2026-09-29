package oneday.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All product tunables in one place. Defaults live in {@code application.properties}; every value
 * here maps to a rule in docs/02-product-blueprint-v2.md or docs/03-technical-architecture-v2.md.
 */
@ConfigurationProperties("oneday")
public record OneDayProperties(
		Security security,
		Registration registration,
		Verification verification,
		Location location,
		Moments moments,
		Discovery discovery,
		Signals signals) {

	/** JWT signing secret (>= 32 bytes) and access-token lifetime. */
	public record Security(String jwtSecret, Duration tokenTtl) {
	}

	/** Signup abuse prevention (blueprint §47.6). */
	public record Registration(int perIpPerHour, String currentConsentVersion) {
	}

	/**
	 * Liveness provider id ({@code dev} or a vendor adapter; {@code none} disables verification) and the
	 * minimum confidence for automatic approval; anything lower goes to manual review.
	 */
	public record Verification(String provider, double autoApproveConfidence, int maxAgeGapYears) {
	}

	/** Location privacy and anti-spoofing (tech arch v2 §5). */
	public record Location(
			String jitterSecret,
			Duration minUpdateInterval,
			double maxSpeedKmh,
			int maxDistinctCellsPerHour) {
	}

	/** Ephemeral content lifetime. */
	public record Moments(Duration ttl) {
	}

	/** Bounded discovery sessions and privacy thresholds. */
	public record Discovery(
			int batchSize,
			int maxPagesPerSession,
			int maxRadiusKm,
			int cityRadiusKm,
			int queriesPerHour,
			int heatKAnonymity) {
	}

	/** Signal budget and the Reaction Window (blueprint v2 §4). */
	public record Signals(int dailyBudget, Duration reactionWindow, int digestBatchSize) {
	}
}
