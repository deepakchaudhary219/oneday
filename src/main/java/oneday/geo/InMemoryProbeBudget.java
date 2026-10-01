package oneday.geo;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import oneday.config.OneDayProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Single-node probe budget, held in memory only and never persisted, so it is not a location history. */
@Component
@ConditionalOnProperty(name = "oneday.state.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryProbeBudget implements ProbeBudget {

	private static final Duration WINDOW = Duration.ofHours(1);

	private final Clock clock;

	private final int maxDistinctCells;

	private final Map<String, Deque<Visit>> visits = new ConcurrentHashMap<>();

	public InMemoryProbeBudget(Clock clock, OneDayProperties properties) {
		this.clock = clock;
		this.maxDistinctCells = properties.location().maxDistinctCellsPerHour();
	}

	@Override
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

	@Override
	public void forget(String userId) {
		visits.remove(userId);
	}

	private record Visit(String cell, long atMillis) {
	}
}
