package oneday.connections;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import oneday.common.ApiException;
import oneday.identity.UserGuard;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionService {

	private final ConnectionRepository connections;

	private final ProfileService profiles;

	private final UserGuard guard;

	private final BlockChecker blocks;

	private final Clock clock;

	public ConnectionService(ConnectionRepository connections, ProfileService profiles, UserGuard guard,
			BlockChecker blocks, Clock clock) {
		this.connections = connections;
		this.profiles = profiles;
		this.guard = guard;
		this.blocks = blocks;
		this.clock = clock;
	}

	/** Creates the pair's connection, or reactivates one that was previously soft-exited. */
	@Transactional
	public Connection connect(String first, String second, ConnectionOrigin origin) {
		String a = first.compareTo(second) < 0 ? first : second;
		String b = first.compareTo(second) < 0 ? second : first;
		Instant now = clock.instant();
		Connection connection = connections.findByUserAAndUserB(a, b)
			.orElseGet(() -> connections.save(new Connection(a, b, origin, now)));
		if (!connection.isActive()) {
			connection.reactivate(origin, now);
		}
		return connection;
	}

	@Transactional(readOnly = true)
	public Connection requireMember(String connectionId, String userId) {
		return connections.findById(connectionId)
			.filter(c -> c.involves(userId))
			.orElseThrow(() -> ApiException.notFound("Connection"));
	}

	@Transactional(readOnly = true)
	public List<Connection> active(String userId) {
		return connections.findByMemberAndState(userId, Connection.State.ACTIVE);
	}

	@Transactional(readOnly = true)
	public List<Connection> all(String userId) {
		return connections.findByMember(userId);
	}

	@Transactional(readOnly = true)
	public Set<String> connectedUserIds(String userId) {
		return active(userId).stream().map(c -> c.otherThan(userId)).collect(Collectors.toSet());
	}

	@Transactional(readOnly = true)
	public boolean areConnected(String a, String b) {
		String lo = a.compareTo(b) < 0 ? a : b;
		String hi = a.compareTo(b) < 0 ? b : a;
		return connections.findByUserAAndUserB(lo, hi).map(Connection::isActive).orElse(false);
	}

	@Transactional(readOnly = true)
	public long countNewSince(String userId, Instant since) {
		return connections.countActiveSince(userId, since);
	}

	/**
	 * Mutual Spark (blueprint v2 §5.2): records private romantic interest. The response only ever says
	 * whether the spark is mutual; a one-sided spark is invisible to the other person.
	 */
	@Transactional
	public SparkView spark(String userId, String connectionId, boolean sparked) {
		guard.requireContactAllowed(userId);
		Connection connection = requireMember(connectionId, userId);
		if (!connection.isActive() || blocks.isBlockedEitherWay(userId, connection.otherThan(userId))) {
			throw ApiException.conflict("CONNECTION_INACTIVE", "This connection is no longer active");
		}
		if (sparked && !profiles.require(userId).isDatingLens()) {
			throw ApiException.conflict("DATING_LENS_OFF", "Turn on the Dating Lens in settings to spark");
		}
		connection.setSpark(userId, sparked);
		return SparkView.of(connection, userId);
	}

	/** Soft exit (blueprint §31.1): ends the connection silently. No notification is sent to anyone. */
	@Transactional
	public void exit(String userId, String connectionId) {
		Connection connection = requireMember(connectionId, userId);
		if (connection.isActive()) {
			connection.end(clock.instant());
		}
	}

	@Transactional
	public void endBetween(String a, String b) {
		String lo = a.compareTo(b) < 0 ? a : b;
		String hi = a.compareTo(b) < 0 ? b : a;
		connections.findByUserAAndUserB(lo, hi).filter(Connection::isActive).ifPresent(c -> c.end(clock.instant()));
	}

	/** Ends every active connection silently (the account is going away). Records are kept. */
	@Transactional
	public void endAllFor(String userId) {
		active(userId).forEach(c -> c.end(clock.instant()));
	}

	@Transactional
	public void deleteAll(List<Connection> toDelete) {
		connections.deleteAll(toDelete);
	}

	public record SparkView(boolean youSparked, boolean mutualSpark, String message) {

		static SparkView of(Connection connection, String userId) {
			boolean mine = connection.hasSparked(userId);
			String message = connection.isMutualSpark() ? "You both sparked ✨"
					: mine ? "Your spark is private. It's only shown if they spark too." : "No spark set";
			return new SparkView(mine, connection.isMutualSpark(), message);
		}
	}
}
