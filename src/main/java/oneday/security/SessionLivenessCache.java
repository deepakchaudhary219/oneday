package oneday.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caches "this session is live" for a few seconds so the per-request session check is a memory read, not a
 * database round trip. Only positive answers are cached. Every way a session ends evicts it here at once, and
 * with the {@code redis} profile the eviction is broadcast to every replica ({@link SessionEvictions}), so
 * sign-out, suspension and theft detection still take effect on the next request everywhere; the TTL bounds
 * the worst case if a broadcast is lost.
 */
@Component
public class SessionLivenessCache {

	static final int MAX_ENTRIES = 200_000;

	private final Map<String, Entry> live = new ConcurrentHashMap<>();

	private final Duration ttl;

	private final Clock clock;

	private final ObjectProvider<SessionEvictions> broadcast;

	public SessionLivenessCache(@Value("${oneday.security.session-cache-ttl:PT15S}") Duration ttl, Clock clock,
			ObjectProvider<SessionEvictions> broadcast) {
		this.ttl = ttl;
		this.clock = clock;
		this.broadcast = broadcast;
	}

	boolean isKnownLive(String sessionId) {
		Entry entry = live.get(sessionId);
		if (entry == null) {
			return false;
		}
		if (!clock.instant().isBefore(entry.until())) {
			live.remove(sessionId, entry);
			return false;
		}
		return true;
	}

	void rememberLive(String sessionId, String userId) {
		if (ttl.isZero() || ttl.isNegative()) {
			return;
		}
		if (live.size() >= MAX_ENTRIES) {
			live.clear(); // crude but safe bound: everything falls back to the database for one TTL
		}
		live.put(sessionId, new Entry(userId, clock.instant().plus(ttl)));
	}

	/** Called on every session end; broadcast to other replicas when a broadcaster is configured. */
	public void evict(String sessionId) {
		evictLocally(sessionId);
		broadcast.ifAvailable(b -> b.sessionEnded(sessionId));
	}

	public void evictUser(String userId) {
		evictUserLocally(userId);
		broadcast.ifAvailable(b -> b.userSessionsEnded(userId));
	}

	public void evictLocally(String sessionId) {
		live.remove(sessionId);
	}

	public void evictUserLocally(String userId) {
		live.values().removeIf(e -> e.userId().equals(userId));
	}

	private record Entry(String userId, Instant until) {
	}
}
