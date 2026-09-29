package oneday.geo;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import oneday.config.OneDayProperties;

import org.springframework.stereotype.Component;

/**
 * Caps how many distinct cells a user can report per hour. Oracle trilateration needs many position
 * probes; this makes each probe scarce. Held in memory only (never persisted, so it is not a location
 * history); M2 moves it to Redis with a 1 h TTL so the cap holds across replicas.
 */
@Component
public class ProbeBudget {

	private static final Duration WINDOW = Duration.ofHours(1);

	private final Clock clock;

	private final int maxDistinctCells;

	private final Map<String, Deque<Visit>> visits = new ConcurrentHashMap<>();

	public ProbeBudget(Clock clock, OneDayProperties properties) {
		this.clock = clock;
		this.maxDistinctCells = properties.location().maxDistinctCellsPerHour();
	}

	/** Returns {@code false} when visiting a new cell would exceed the hourly budget. */
	public boolean tryVisit(String userId, String cell) {
		long now = clock.millis();
		Deque<Visit> recent = visits.computeIfAbsent(userId, k -> new ArrayDeque<>());
		synchronized (recent) {
			while (!recent.isEmpty() && recent.peekFirst().atMillis() <= now - WINDOW.toMillis()) {
				recent.pollFirst();
			}
			if (recent.stream().anyMatch(v -> v.cell().equals(cell))) {
				return true;
			}
			if (recent.size() >= maxDistinctCells) {
				return false;
			}
			recent.addLast(new Visit(cell, now));
			return true;
		}
	}

	public void forget(String userId) {
		visits.remove(userId);
	}

	private record Visit(String cell, long atMillis) {
	}
}
