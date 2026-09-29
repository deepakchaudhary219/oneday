package oneday.common;

import java.time.Duration;

/**
 * Sliding-window rate limiting. {@code oneday.state.store=memory} (default, single node) or
 * {@code redis} (shared across replicas; tech arch v2 §1.2).
 */
public interface RateLimiter {

	/** Records an attempt and returns {@code false} when {@code limit} attempts already happened in {@code window}. */
	boolean tryAcquire(String key, int limit, Duration window);
}
