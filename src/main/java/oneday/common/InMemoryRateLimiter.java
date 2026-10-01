package oneday.common;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Single-node limiter for local development and the one-replica pilot. */
@Component
@ConditionalOnProperty(name = "oneday.state.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

	private final Clock clock;

	private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

	public InMemoryRateLimiter(Clock clock) {
		this.clock = clock;
	}

	@Override
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
