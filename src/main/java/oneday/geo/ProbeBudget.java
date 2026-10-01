package oneday.geo;

/**
 * Caps how many distinct cells a user can report per hour. Oracle trilateration needs many position
 * probes; this makes each probe scarce (tech arch v2 §5.1).
 */
public interface ProbeBudget {

	/** Returns {@code false} when visiting a new cell would exceed the hourly budget. */
	boolean tryVisit(String userId, String cell);

	/** Drops everything held for the user (erasure). */
	void forget(String userId);
}
