package oneday.rightnow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import oneday.chat.ChatService;
import oneday.cities.CityFeature;
import oneday.cities.CityGates;
import oneday.chat.Conversation;
import oneday.common.ApiException;
import oneday.connections.Connection;
import oneday.connections.ConnectionOrigin;
import oneday.connections.ConnectionService;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
import oneday.geo.Direction;
import oneday.geo.DistanceBand;
import oneday.geo.GeoCell;
import oneday.geo.LocationPrivacy;
import oneday.geo.LocationPrivacy.Placement;
import oneday.geo.LocationPrivacy.Precision;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.notify.NotificationService;
import oneday.plus.Entitlements;
import oneday.profile.ActivityTags;
import oneday.profile.Affinity;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Right Now (blueprint v2 §6.1): "I'm up for a badminton game for the next hour." It works only with real
 * density, so it is off until Gate 2 ({@code oneday.right-now.enabled}).
 *
 * <p>
 * The same safety model as the rest of discovery applies: nearby people see first name, activity, distance
 * band and coarse direction (never coordinates); "I'm up for it too" is a request with a small daily budget
 * and no free text; only the poster's acceptance creates a Connection, and a decline is silent. Sessions
 * end on their own after at most two hours.
 */
@Service
public class RightNowService {

	static final Duration MIN = Duration.ofMinutes(30);

	static final Duration MAX = Duration.ofMinutes(120);

	static final int JOINS_PER_DAY = 5;

	private final RightNowSessionRepository sessions;

	private final RightNowJoinRepository joins;

	private final RightNowProperties settings;

	private final LocationService locations;

	private final LocationPrivacy privacy;

	private final ProfileService profiles;

	private final ConnectionService connections;

	private final ChatService chat;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final NotificationService notifications;

	private final EventPublisher events;

	private final Clock clock;

	private final Entitlements entitlements;

	private final CityGates cities;

	public RightNowService(RightNowSessionRepository sessions, RightNowJoinRepository joins,
			RightNowProperties settings, LocationService locations, LocationPrivacy privacy, ProfileService profiles,
			ConnectionService connections, ChatService chat, BlockChecker blocks, UserGuard guard,
			NotificationService notifications, EventPublisher events, Clock clock, Entitlements entitlements,
			CityGates cities) {
		this.cities = cities;
		this.entitlements = entitlements;
		this.sessions = sessions;
		this.joins = joins;
		this.settings = settings;
		this.locations = locations;
		this.privacy = privacy;
		this.profiles = profiles;
		this.connections = connections;
		this.chat = chat;
		this.blocks = blocks;
		this.guard = guard;
		this.notifications = notifications;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public MySessionView start(String userId, String rawActivity, int minutes) {
		requireEnabled(userId);
		guard.requireContactAllowed(userId);
		locations.requireCurrentCell(userId);
		String activity = ActivityTags.normalize(rawActivity);
		if (activity == null) {
			throw ApiException.badRequest("ACTIVITY_REQUIRED", "Name the activity you're up for");
		}
		Duration length = Duration.ofMinutes(minutes);
		if (length.compareTo(MIN) < 0 || length.compareTo(MAX) > 0) {
			throw ApiException.unprocessable("INVALID_DURATION", "Right Now lasts 30 to 120 minutes");
		}
		Instant now = clock.instant();
		sessions.findActiveFor(userId, now).ifPresent(s -> s.end(now));
		RightNowSession session = sessions.save(new RightNowSession(userId, activity, now, now.plus(length)));
		return mine(userId, session);
	}

	@Transactional
	public void stop(String userId) {
		sessions.findActiveFor(userId, clock.instant()).ifPresent(s -> s.end(clock.instant()));
	}

	@Transactional(readOnly = true)
	public Optional<MySessionView> current(String userId) {
		requireEnabled(userId);
		return sessions.findActiveFor(userId, clock.instant()).map(s -> mine(userId, s));
	}

	/** People nearby who are up for something now, best shared context first. Bounded; band precision. */
	@Transactional(readOnly = true)
	public List<NearbyView> nearby(String viewerId, String activityFilter) {
		requireEnabled(viewerId);
		guard.requireActive(viewerId);
		Instant now = clock.instant();
		if (!connections.inCouple(List.of(viewerId)).isEmpty()) {
			return List.of();
		}
		Profile viewer = profiles.require(viewerId);
		GeoCell here = locations.requireCurrentCell(viewerId);
		String activity = ActivityTags.normalize(activityFilter);
		List<RightNowSession> active = visibleTo(viewerId, sessions.findActive(now));
		Map<String, GeoCell> cells = locations.currentCells(active.stream().map(RightNowSession::getUserId).toList());
		Set<String> joined = joins
			.findByJoinerIdAndSessionIdIn(viewerId,
					active.isEmpty() ? List.of("-") : active.stream().map(RightNowSession::getId).toList())
			.stream()
			.map(RightNowJoin::getSessionId)
			.collect(Collectors.toSet());
		double radius = Math.min(viewer.getDiscoveryRadiusKm(), entitlements.maxRadiusKm(viewerId));
		List<Ranked> found = new ArrayList<>();
		for (RightNowSession s : active) {
			GeoCell cell = cells.get(s.getUserId());
			if (cell == null || (activity != null && !activity.equals(s.getActivity()))) {
				continue;
			}
			Profile owner = profiles.require(s.getUserId());
			Optional<Placement> placement = privacy.place(here, cell, viewerId, s.getUserId(), Precision.BAND,
					owner.isInSafeZone(cell.cell()), radius);
			if (placement.isEmpty()) {
				continue;
			}
			Affinity affinity = Affinity.between(viewer, owner);
			found.add(new Ranked(new NearbyView(s.getId(), owner.firstName(), s.getActivity(), placement.get().band(),
					placement.get().band().label(), placement.get().direction(), timeLeft(s, now),
					affinity.explanation(s.getActivity()), joined.contains(s.getId())), affinity.rank()));
		}
		return found.stream()
			.sorted(Comparator.comparingInt(Ranked::rank).reversed())
			.limit(settings.maxResults())
			.map(Ranked::view)
			.toList();
	}

	/** "I'm up for it too." One per session, a small daily budget, no free text. */
	@Transactional
	public JoinView join(String userId, String sessionId) {
		requireEnabled(userId);
		guard.requireContactAllowed(userId);
		Instant now = clock.instant();
		RightNowSession session = sessions.findById(sessionId)
			.filter(s -> s.isActive(now))
			.filter(s -> !visibleTo(userId, List.of(s)).isEmpty())
			.orElseThrow(() -> ApiException.notFound("Right Now"));
		if (joins.existsBySessionIdAndJoinerId(sessionId, userId)) {
			throw ApiException.conflict("ALREADY_ASKED", "You've already said you're up for it");
		}
		if (joins.countByJoinerIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(24))) >= JOINS_PER_DAY) {
			throw ApiException.tooManyRequests("RIGHT_NOW_BUDGET",
					"You've used today's Right Now requests. More tomorrow.");
		}
		joins.save(new RightNowJoin(sessionId, userId, now));
		notifications.requestPush(session.getUserId(), "Right Now", "Someone nearby is up for it too",
				Map.of("open", "right-now"));
		return new JoinView("Sent. If they're up for it, you'll be connected.");
	}

	/** Pending "up for it too" requests on the caller's own active session. */
	@Transactional(readOnly = true)
	public List<RequestView> requests(String userId) {
		requireEnabled(userId);
		Instant now = clock.instant();
		Optional<RightNowSession> session = sessions.findActiveFor(userId, now);
		if (session.isEmpty()) {
			return List.of();
		}
		Profile me = profiles.require(userId);
		Set<String> blocked = blocks.blockedEitherWay(userId);
		List<RightNowJoin> pending = joins.findBySessionIdAndStatus(session.get().getId(), RightNowJoin.Status.PENDING)
			.stream()
			.filter(j -> !blocked.contains(j.getJoinerId()))
			.toList();
		Set<String> reachable = guard.reachableAmong(pending.stream().map(RightNowJoin::getJoinerId).toList());
		return pending.stream().filter(j -> reachable.contains(j.getJoinerId())).map(j -> {
			Profile joiner = profiles.require(j.getJoinerId());
			return new RequestView(j.getId(), joiner.firstName(), Affinity.between(me, joiner)
				.explanation(session.get().getActivity()));
		}).toList();
	}

	/** Accepting creates the Connection and a Conversation seeded with the activity. */
	@Transactional
	public AcceptView accept(String userId, String joinId) {
		requireEnabled(userId);
		guard.requireContactAllowed(userId);
		RightNowJoin join = pendingJoinFor(userId, joinId);
		RightNowSession session = sessions.findById(join.getSessionId()).orElseThrow();
		String joinerId = join.getJoinerId();
		if (blocks.isBlockedEitherWay(userId, joinerId) || guard.reachableAmong(List.of(joinerId)).isEmpty()) {
			throw ApiException.notFound("Request");
		}
		Connection connection = connections.connect(userId, joinerId, ConnectionOrigin.MUTUAL_REVEAL);
		Conversation conversation = chat.openFor(connection, "Connected over: " + session.getActivity() + ", right now");
		join.resolve(RightNowJoin.Status.ACCEPTED);
		events.publish(new DomainEvent.MutualRevealed(connection.getId(), connection.getUserA(), connection.getUserB()));
		return new AcceptView(connection.getId(), conversation.getId(), conversation.getSeedContext());
	}

	/** Silent: the joiner is never told. */
	@Transactional
	public void decline(String userId, String joinId) {
		requireEnabled(userId);
		pendingJoinFor(userId, joinId).resolve(RightNowJoin.Status.DECLINED);
	}

	/** The person behind a Right Now session (for block and report). */
	@Transactional(readOnly = true)
	public Optional<String> ownerOf(String sessionId) {
		return sessions.findById(sessionId).map(RightNowSession::getUserId);
	}

	@Transactional
	public void forget(String userId) {
		List<String> mine = sessions.findByUserId(userId).stream().map(RightNowSession::getId).toList();
		joins.deleteInvolving(userId, mine.isEmpty() ? List.of("-") : mine);
		sessions.deleteByUserId(userId);
	}

	private RightNowJoin pendingJoinFor(String ownerId, String joinId) {
		Instant now = clock.instant();
		return joins.findById(joinId)
			.filter(j -> j.getStatus() == RightNowJoin.Status.PENDING)
			.filter(j -> sessions.findById(j.getSessionId())
				.filter(s -> s.getUserId().equals(ownerId) && s.isActive(now))
				.isPresent())
			.orElseThrow(() -> ApiException.notFound("Request"));
	}

	/** Sessions the viewer may see: not their own, not blocked, not connected already, not in Couple Mode. */
	private List<RightNowSession> visibleTo(String viewerId, List<RightNowSession> candidates) {
		Set<String> owners = candidates.stream().map(RightNowSession::getUserId).collect(Collectors.toSet());
		Set<String> ok = new HashSet<>(guard.reachableAmong(owners));
		ok.removeAll(blocks.blockedEitherWay(viewerId));
		ok.removeAll(connections.connectedUserIds(viewerId));
		ok.removeAll(connections.inCouple(ok));
		ok.remove(viewerId);
		return candidates.stream().filter(s -> ok.contains(s.getUserId())).toList();
	}

	private MySessionView mine(String userId, RightNowSession s) {
		Instant now = clock.instant();
		long pending = joins.findBySessionIdAndStatus(s.getId(), RightNowJoin.Status.PENDING).size();
		return new MySessionView(s.getActivity(), s.getEndsAt(), timeLeft(s, now), pending > 9 ? "9+" : String.valueOf(pending));
	}

	/** Coarse on purpose: "about an hour", never a ticking countdown. */
	private static String timeLeft(RightNowSession s, Instant now) {
		long minutes = Duration.between(now, s.getEndsAt()).toMinutes();
		return minutes >= 75 ? "for a while yet" : minutes >= 40 ? "for about an hour" : "for a little longer";
	}

	/** On everywhere once Gate 2 passes globally, or per city as each city reaches density (multi-city). */
	private void requireEnabled(String userId) {
		if (!settings.enabled() && !cities.isEnabled(userId, CityFeature.RIGHT_NOW)) {
			throw ApiException.notFound("Right Now");
		}
	}

	private record Ranked(NearbyView view, int rank) {
	}

	public record MySessionView(String activity, Instant endsAt, String timeLeft, String requests) {
	}

	/** {@code id} is the handle for "I'm up for it too". No coordinates, no internal user id. */
	public record NearbyView(String id, String firstName, String activity, DistanceBand band, String distance,
			Direction direction, String timeLeft, String whyYouSeeThis, boolean youAsked) {
	}

	public record JoinView(String message) {
	}

	public record RequestView(String requestId, String firstName, String sharedContext) {
	}

	public record AcceptView(String connectionId, String conversationId, String seedContext) {
	}
}
