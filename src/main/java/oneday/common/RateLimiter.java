package oneday.common;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Sliding-window rate limiter. In-memory for the single-node pilot; M2 swaps this for a Redis-backed
 * implementation so limits hold across replicas (tech arch v2 §1.2).
 */
@Component
public class RateLimiter {

	private final Clock clock;

	private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** Records an attempt and returns {@code false} when {@code limit} attempts already happened in {@code window}. */
	public boolean tryAcquire(String key, int limit, Duration window) {
		long now = clock.millis();
		long cutoff = now - window.toMillis();
		Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (hits) {
			while (!hits.isEmpty() && hits.peekFirst() <= cutoff) {
				hits.pollFirst();
			}
			if (hits.size() >= limit) {
				return false;
			}
			hits.addLast(now);
			return true;
		}
	}
}
