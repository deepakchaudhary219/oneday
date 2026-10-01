package oneday.connections;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import oneday.common.ApiException;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
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

	private final EventPublisher events;

	public ConnectionService(ConnectionRepository connections, ProfileService profiles, UserGuard guard,
			BlockChecker blocks, Clock clock, EventPublisher events) {
		this.events = events;
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
		boolean wasMutual = connection.isMutualSpark();
		connection.setSpark(userId, sparked);
		if (!wasMutual && connection.isMutualSpark()) {
			events.publish(new DomainEvent.MutualSparked(connection.getId(), connection.getUserA(),
					connection.getUserB()));
		}
		return SparkView.of(connection, userId);
	}

	/**
	 * Couple Mode (blueprint v2 §7.4): "we're seeing each other". Needs a Mutual Spark. Each confirmation is
	 * private until both confirm; then both people leave Discovery Mode (they neither see strangers nor are
	 * seen) while Friend Mode keeps working. Either side can switch it off at any time, silently.
	 */
	@Transactional
	public CoupleView couple(String userId, String connectionId, boolean confirmed) {
		guard.requireContactAllowed(userId);
		Connection connection = requireMember(connectionId, userId);
		if (!connection.isActive() || blocks.isBlockedEitherWay(userId, connection.otherThan(userId))) {
			throw ApiException.conflict("CONNECTION_INACTIVE", "This connection is no longer active");
		}
		if (confirmed && !connection.isMutualSpark()) {
			throw ApiException.conflict("MUTUAL_SPARK_REQUIRED", "Couple Mode is for connections where you both sparked");
		}
		boolean wasCouple = connection.isCouple();
		connection.setCouple(userId, confirmed, clock.instant());
		if (!wasCouple && connection.isCouple()) {
			events.publish(new DomainEvent.CoupleFormed(connection.getId(), connection.getUserA(),
					connection.getUserB()));
		}
		return CoupleView.of(connection, userId);
	}

	/**
	 * DPDP withdrawal of {@code DATING_PREFERENCES}: the person's sparks and Couple Mode confirmations are
	 * withdrawn on every connection. Silent, like switching a spark off by hand.
	 */
	@Transactional
	public void withdrawSparks(String userId) {
		connections.findByMember(userId).forEach(c -> c.setSpark(userId, false));
	}

	/** The subset of {@code userIds} currently in Couple Mode (hidden from, and not shown, Discovery). */
	@Transactional(readOnly = true)
	public Set<String> inCouple(Collection<String> userIds) {
		if (userIds.isEmpty()) {
			return Set.of();
		}
		Set<String> ids = new HashSet<>(userIds);
		Set<String> result = new HashSet<>();
		for (Connection c : connections.findCouplesAmong(ids)) {
			if (ids.contains(c.getUserA())) {
				result.add(c.getUserA());
			}
			if (ids.contains(c.getUserB())) {
				result.add(c.getUserB());
			}
		}
		return result;
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

	public record CoupleView(boolean youConfirmed, boolean coupleMode, String message) {

		static CoupleView of(Connection connection, String userId) {
			boolean mine = connection.hasConfirmedCouple(userId);
			String message = connection.isCouple()
					? "Couple Mode is on. Discovery is paused for you both; your chats and memories stay."
					: mine ? "Your answer is private until they confirm too." : "Couple Mode is off";
			return new CoupleView(mine, connection.isCouple(), message);
		}
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
