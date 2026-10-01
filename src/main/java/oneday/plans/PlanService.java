package oneday.plans;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.connections.ConnectionService;
import oneday.dates.MeetingPoint;
import oneday.dates.MeetingPointService;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
import oneday.geo.GeoCell;
import oneday.geo.GeoMath;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.notify.NotificationService;
import oneday.profile.ActivityTags;
import oneday.realtime.RealtimeService;
import oneday.profile.Affinity;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans &amp; Rooms (blueprint v2 §11): "Filter coffee + chess at Cubbon Park, Saturday 5 pm, 6 people".
 *
 * <p>
 * Group meet-ups are the gentlest way to meet strangers (safety in numbers, a shared activity, a public
 * place), so they are built on the same rules as the rest of the product:
 * <ul>
 * <li>Only at Safety-Verified Meeting Points, so the only position ever shown is a public venue.</li>
 * <li>Joining is a request; the host approves. A decline is silent, like a pass.</li>
 * <li>Only approved members see who else is coming (first names) and the Room.</li>
 * <li>A Roots plan is visible only to people from the same home region.</li>
 * <li>Blocks hide plans both ways; the Room is deleted a day after the plan ends.</li>
 * </ul>
 */
@Service
public class PlanService {

	static final int MIN_CAPACITY = 3;

	static final int MAX_CAPACITY = 12;

	static final int MAX_OPEN_HOSTED = 2;

	static final int REQUESTS_PER_DAY = 10;

	static final Duration MAX_ADVANCE = Duration.ofDays(14);

	static final Duration MAX_LENGTH = Duration.ofHours(6);

	static final Duration ROOM_AFTERLIFE = Duration.ofHours(24);

	static final Set<PlanMember.Status> IN = EnumSet.of(PlanMember.Status.HOST, PlanMember.Status.APPROVED);

	private final PlanRepository plans;

	private final PlanMemberRepository members;

	private final PlanMessageRepository messages;

	private final MeetingPointService meetingPoints;

	private final LocationService locations;

	private final ProfileService profiles;

	private final ConnectionService connections;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final NotificationService notifications;

	private final EventPublisher events;

	private final Clock clock;

	private final RealtimeService realtime;

	private final RateLimiter rateLimiter;

	public PlanService(PlanRepository plans, PlanMemberRepository members, PlanMessageRepository messages,
			MeetingPointService meetingPoints, LocationService locations, ProfileService profiles,
			ConnectionService connections, BlockChecker blocks, UserGuard guard, NotificationService notifications,
			EventPublisher events, Clock clock, RealtimeService realtime, RateLimiter rateLimiter) {
		this.realtime = realtime;
		this.rateLimiter = rateLimiter;
		this.plans = plans;
		this.members = members;
		this.messages = messages;
		this.meetingPoints = meetingPoints;
		this.locations = locations;
		this.profiles = profiles;
		this.connections = connections;
		this.blocks = blocks;
		this.guard = guard;
		this.notifications = notifications;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public PlanView host(String userId, String meetingPointId, String rawActivity, String title, boolean rootsOnly,
			int capacity, Instant startsAt, Instant endsAt) {
		guard.requireContactAllowed(userId);
		Instant now = clock.instant();
		MeetingPoint point = meetingPoints.findActive(meetingPointId)
			.orElseThrow(() -> ApiException.notFound("Meeting point"));
		String activity = ActivityTags.normalize(rawActivity);
		if (activity == null) {
			throw ApiException.badRequest("ACTIVITY_REQUIRED", "Name the activity");
		}
		if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
			throw ApiException.unprocessable("INVALID_CAPACITY", "Plans are for 3 to 12 people");
		}
		if (startsAt == null || endsAt == null || startsAt.isBefore(now) || startsAt.isAfter(now.plus(MAX_ADVANCE))
				|| !endsAt.isAfter(startsAt) || Duration.between(startsAt, endsAt).compareTo(MAX_LENGTH) > 0) {
			throw ApiException.unprocessable("INVALID_TIME", "Plans start within 14 days and last up to 6 hours");
		}
		if (plans.countByHostIdAndStatusAndEndsAtAfter(userId, Plan.Status.OPEN, now) >= MAX_OPEN_HOSTED) {
			throw ApiException.conflict("TOO_MANY_PLANS", "You're already hosting two plans");
		}
		String region = null;
		if (rootsOnly) {
			region = profiles.require(userId).getHomeRegion();
			if (region == null) {
				throw ApiException.conflict("HOME_REGION_REQUIRED", "Add your home region to host a Roots plan");
			}
		}
		String name = title == null || title.isBlank() ? activity + " at " + point.getName() : title.strip();
		Plan plan = plans.save(new Plan(userId, point.getId(), activity, name, region, capacity, startsAt, endsAt,
				point.getLat(), point.getLon(), now));
		members.save(new PlanMember(plan.getId(), userId, PlanMember.Status.HOST, now));
		return view(plan, userId);
	}

	/** Open plans near the viewer, best shared context first. Positions are venues, never people. */
	@Transactional(readOnly = true)
	public List<PlanCard> nearby(String userId, String activityFilter, int radiusKm) {
		guard.requireActive(userId);
		GeoCell here = locations.requireCurrentCell(userId);
		Profile me = profiles.require(userId);
		double radius = Math.clamp(radiusKm, 1, 40);
		double[] box = GeoMath.boundingBoxDegrees(here.lat(), radius);
		String activity = ActivityTags.normalize(activityFilter);
		Instant now = clock.instant();
		Set<String> blocked = blocks.blockedEitherWay(userId);
		List<Plan> found = plans.findOpenInBox(now, here.lat() - box[0], here.lat() + box[0], here.lon() - box[1],
				here.lon() + box[1], PageRequest.of(0, 200));
		Set<String> reachableHosts = guard.reachableAmong(found.stream().map(Plan::getHostId).toList());
		List<Ranked> cards = new ArrayList<>();
		for (Plan p : found) {
			if (blocked.contains(p.getHostId()) || !reachableHosts.contains(p.getHostId())
					|| (activity != null && !activity.equals(p.getActivity()))
					|| (p.getRootsRegion() != null && !p.getRootsRegion().equals(me.getHomeRegion()))) {
				continue;
			}
			double km = GeoMath.haversineKm(here.lat(), here.lon(), p.getLat(), p.getLon());
			if (km > radius || blockedMemberInside(p, blocked)) {
				continue;
			}
			Profile host = profiles.require(p.getHostId());
			Affinity affinity = Affinity.between(me, host);
			cards.add(new Ranked(card(p, userId, host, km, affinity.explanation(p.getActivity())),
					affinity.rank() * 10 - (int) km));
		}
		return cards.stream().sorted(Comparator.comparingInt(Ranked::rank).reversed()).limit(30).map(Ranked::card).toList();
	}

	@Transactional
	public PlanView requestToJoin(String userId, String planId) {
		guard.requireContactAllowed(userId);
		Plan plan = requireVisible(userId, planId);
		Instant now = clock.instant();
		if (members.existsById(new PlanMember.Key(planId, userId))) {
			throw ApiException.conflict("ALREADY_ASKED", "You've already asked to join");
		}
		if (spotsLeft(plan) <= 0) {
			throw ApiException.conflict("PLAN_FULL", "This plan is full");
		}
		if (members.countByKeyUserIdAndStatusAndCreatedAtAfter(userId, PlanMember.Status.REQUESTED,
				now.minus(Duration.ofHours(24))) >= REQUESTS_PER_DAY) {
			throw ApiException.tooManyRequests("PLAN_REQUEST_BUDGET", "You've asked to join enough plans today");
		}
		members.save(new PlanMember(planId, userId, PlanMember.Status.REQUESTED, now));
		notifications.requestPush(plan.getHostId(), "Plans", "Someone wants to join your plan",
				Map.of("open", "plans", "planId", planId));
		return view(plan, userId);
	}

	@Transactional(readOnly = true)
	public List<JoinRequest> requests(String hostId, String planId) {
		Plan plan = requireHost(hostId, planId);
		Profile host = profiles.require(hostId);
		Set<String> blocked = blocks.blockedEitherWay(hostId);
		List<PlanMember> pending = members.findByKeyPlanIdAndStatus(plan.getId(), PlanMember.Status.REQUESTED)
			.stream()
			.filter(m -> !blocked.contains(m.getUserId()))
			.toList();
		Set<String> reachable = guard.reachableAmong(pending.stream().map(PlanMember::getUserId).toList());
		return pending.stream().filter(m -> reachable.contains(m.getUserId())).map(m -> {
			Profile p = profiles.require(m.getUserId());
			Affinity affinity = Affinity.between(host, p);
			return new JoinRequest(requestHandle(plan, m), p.firstName(),
					affinity.explanation(plan.getActivity()), affinity.rootsMatch());
		}).toList();
	}

	/** Approves (or silently declines) a request, addressed by its opaque handle. */
	@Transactional
	public PlanView decide(String hostId, String planId, String handle, boolean approve) {
		Plan plan = requireHost(hostId, planId);
		PlanMember request = members.findByKeyPlanIdAndStatus(planId, PlanMember.Status.REQUESTED)
			.stream()
			.filter(m -> requestHandle(plan, m).equals(handle))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("Request"));
		if (approve) {
			if (spotsLeft(plan) <= 0) {
				throw ApiException.conflict("PLAN_FULL", "This plan is full");
			}
			if (blocks.isBlockedEitherWay(hostId, request.getUserId())) {
				throw ApiException.notFound("Request");
			}
			request.setStatus(PlanMember.Status.APPROVED);
			events.publish(new DomainEvent.PlanJoined(planId, hostId, request.getUserId()));
			notifications.requestPush(request.getUserId(), "Plans", "You're in! Say hi in the Room",
					Map.of("open", "plans", "planId", planId));
		}
		else {
			request.setStatus(PlanMember.Status.DECLINED);
		}
		return view(plan, hostId);
	}

	@Transactional
	public void leave(String userId, String planId) {
		Plan plan = plans.findById(planId).orElseThrow(() -> ApiException.notFound("Plan"));
		if (plan.getHostId().equals(userId)) {
			plan.cancel();
			return;
		}
		members.findById(new PlanMember.Key(planId, userId))
			.filter(m -> m.getStatus() != PlanMember.Status.DECLINED)
			.ifPresent(m -> m.setStatus(PlanMember.Status.LEFT));
	}

	@Transactional(readOnly = true)
	public PlanView get(String userId, String planId) {
		guard.requireActive(userId);
		Plan plan = plans.findById(planId).orElseThrow(() -> ApiException.notFound("Plan"));
		boolean member = isIn(plan, userId);
		if (!member) {
			requireVisible(userId, planId);
		}
		return view(plan, userId);
	}

	/** Plans the caller hosts or was approved into, from a day ago onwards. */
	@Transactional(readOnly = true)
	public List<PlanView> mine(String userId) {
		guard.requireExisting(userId);
		return plans.findForMember(userId, IN, clock.instant().minus(Duration.ofDays(1)))
			.stream()
			.map(p -> view(p, userId))
			.toList();
	}

	// ---- the Room -----------------------------------------------------------------------------------

	@Transactional
	public RoomMessage say(String userId, String planId, String body) {
		guard.requireContactAllowed(userId);
		Plan plan = requireRoom(userId, planId);
		if (!plan.isOpen(clock.instant())) {
			throw ApiException.conflict("ROOM_CLOSED", "This plan is over");
		}
		if (!rateLimiter.tryAcquire("room:" + planId + ":" + userId, 10, Duration.ofMinutes(1))) {
			throw ApiException.tooManyRequests("PACING_SLOW_DOWN", "Slow down a little. Messages land better one at a time.");
		}
		PlanMessage message = messages.save(new PlanMessage(planId, userId, body.strip(), clock.instant()));
		String name = profiles.require(userId).firstName();
		// Every member who hasn't blocked (or been blocked by) the sender gets it live.
		Set<String> blocked = blocks.blockedEitherWay(userId);
		members.findByKeyPlanId(planId)
			.stream()
			.filter(m -> m.isIn() && !blocked.contains(m.getUserId()))
			.forEach(m -> realtime.toUser(m.getUserId(), "room",
					new RealtimeRoomMessage(planId, RoomMessage.of(message, m.getUserId(), name))));
		return RoomMessage.of(message, userId, name);
	}

	/** The Room, newest first. Messages from people the viewer blocked (either way) are hidden. */
	@Transactional(readOnly = true)
	public List<RoomMessage> room(String userId, String planId, Instant before, int limit) {
		requireRoom(userId, planId);
		Set<String> blocked = blocks.blockedEitherWay(userId);
		Instant cursor = before == null ? clock.instant().plusSeconds(1) : before;
		return messages
			.findByPlanIdAndCreatedAtBeforeOrderByCreatedAtDesc(planId, cursor, PageRequest.of(0, Math.clamp(limit, 1, 50)))
			.stream()
			.filter(m -> !blocked.contains(m.getSenderId()))
			.map(m -> RoomMessage.of(m, userId, profiles.find(m.getSenderId()).map(Profile::firstName).orElse("Someone")))
			.toList();
	}

	// ---- lifecycle ----------------------------------------------------------------------------------

	/** Ends plans whose time passed, and deletes Rooms (and plans) a day after they end. */
	@Scheduled(fixedDelayString = "PT10M", initialDelayString = "${oneday.plans.sweep-initial-delay:PT5M}")
	@Transactional
	public void sweep() {
		Instant now = clock.instant();
		plans.findByStatusAndEndsAtLessThanEqual(Plan.Status.OPEN, now).forEach(Plan::end);
		List<String> gone = plans.findByEndsAtBefore(now.minus(ROOM_AFTERLIFE)).stream().map(Plan::getId).toList();
		if (!gone.isEmpty()) {
			messages.deleteByPlanIds(gone);
			members.deleteByPlanIds(gone);
			plans.deleteAllById(gone);
		}
	}

	@Transactional
	public void forget(String userId) {
		List<String> hosted = plans.findByHostId(userId).stream().map(Plan::getId).toList();
		if (!hosted.isEmpty()) {
			messages.deleteByPlanIds(hosted);
			members.deleteByPlanIds(hosted);
			plans.deleteAllById(hosted);
		}
		messages.deleteBySenderId(userId);
		members.deleteByUserId(userId);
	}

	// ---- internals ----------------------------------------------------------------------------------

	private Plan requireVisible(String userId, String planId) {
		Instant now = clock.instant();
		Plan plan = plans.findById(planId).filter(p -> p.isOpen(now)).orElseThrow(() -> ApiException.notFound("Plan"));
		Set<String> blocked = blocks.blockedEitherWay(userId);
		Profile me = profiles.require(userId);
		if (plan.getHostId().equals(userId) || blocked.contains(plan.getHostId())
				|| (plan.getRootsRegion() != null && !plan.getRootsRegion().equals(me.getHomeRegion()))
				|| blockedMemberInside(plan, blocked) || !connections.inCouple(List.of(userId)).isEmpty()
				|| guard.reachableAmong(List.of(plan.getHostId())).isEmpty()) {
			throw ApiException.notFound("Plan");
		}
		return plan;
	}

	private Plan requireHost(String userId, String planId) {
		return plans.findById(planId)
			.filter(p -> p.getHostId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("Plan"));
	}

	private Plan requireRoom(String userId, String planId) {
		guard.requireExisting(userId);
		Plan plan = plans.findById(planId).orElseThrow(() -> ApiException.notFound("Plan"));
		if (!isIn(plan, userId)) {
			throw ApiException.notFound("Plan");
		}
		return plan;
	}

	private boolean isIn(Plan plan, String userId) {
		return members.findById(new PlanMember.Key(plan.getId(), userId)).map(PlanMember::isIn).orElse(false);
	}

	/** Someone the viewer blocked (or who blocked them) is already in: the plan is not shown to either. */
	private boolean blockedMemberInside(Plan plan, Set<String> blocked) {
		if (blocked.isEmpty()) {
			return false;
		}
		return members.findByKeyPlanId(plan.getId())
			.stream()
			.anyMatch(m -> m.isIn() && blocked.contains(m.getUserId()));
	}

	private int spotsLeft(Plan plan) {
		return plan.getCapacity() - (int) members.countByKeyPlanIdAndStatusIn(plan.getId(), IN);
	}

	/** Requests are addressed by an opaque per-plan handle: hosts never see joiners' internal ids. */
	private static String requestHandle(Plan plan, PlanMember member) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest((plan.getId() + "|" + member.getUserId()).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 12);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private PlanCard card(Plan p, String viewerId, Profile host, double km, String why) {
		String status = members.findById(new PlanMember.Key(p.getId(), viewerId))
			.map(m -> m.getStatus() == PlanMember.Status.APPROVED ? "IN"
					: m.getStatus() == PlanMember.Status.LEFT ? null : "ASKED")
			.orElse(null);
		return new PlanCard(p.getId(), p.getTitle(), p.getActivity(), host.firstName(), placeName(p),
				Math.round(km * 10) / 10.0, p.getStartsAt(), p.getEndsAt(), spotsLeft(p), p.getRootsRegion() != null,
				why, status);
	}

	private String placeName(Plan p) {
		return meetingPoints.find(p.getMeetingPointId()).map(MeetingPoint::getName).orElse("A verified meeting point");
	}

	private PlanView view(Plan plan, String viewerId) {
		boolean member = isIn(plan, viewerId);
		List<String> going = new ArrayList<>();
		if (member) {
			Set<String> blocked = new HashSet<>(blocks.blockedEitherWay(viewerId));
			members.findByKeyPlanId(plan.getId())
				.stream()
				.filter(m -> m.isIn() && !blocked.contains(m.getUserId()))
				.forEach(m -> profiles.find(m.getUserId()).ifPresent(p -> going.add(p.firstName())));
		}
		MeetingPoint point = meetingPoints.find(plan.getMeetingPointId()).orElse(null);
		String yourStatus = members.findById(new PlanMember.Key(plan.getId(), viewerId))
			.map(m -> m.getStatus() == PlanMember.Status.DECLINED ? PlanMember.Status.REQUESTED : m.getStatus())
			.map(Enum::name)
			.orElse(null);
		return new PlanView(plan.getId(), plan.getTitle(), plan.getActivity(),
				profiles.find(plan.getHostId()).map(Profile::firstName).orElse(null),
				point == null ? null : point.getName(), point == null ? null : point.getAddress(), plan.getLat(),
				plan.getLon(), plan.getStartsAt(), plan.getEndsAt(), plan.getCapacity(), spotsLeft(plan),
				plan.getRootsRegion() != null, plan.getStatus(), yourStatus, member ? going : List.of());
	}

	private record Ranked(PlanCard card, int rank) {
	}

	/** A plan in the nearby list. {@code you} is null, ASKED or IN; a decline reads as ASKED forever. */
	public record PlanCard(String id, String title, String activity, String hostFirstName, String place,
			double distanceKm, Instant startsAt, Instant endsAt, int spotsLeft, boolean rootsPlan, String whyYouSeeThis,
			String you) {
	}

	/**
	 * The full plan. {@code going} (first names) is shown only to members; {@code yourStatus} never reveals a
	 * decline. The coordinates are the venue's.
	 */
	public record PlanView(String id, String title, String activity, String hostFirstName, String place,
			String address, double venueLat, double venueLon, Instant startsAt, Instant endsAt, int capacity,
			int spotsLeft, boolean rootsPlan, Plan.Status status, String yourStatus, List<String> going) {
	}

	public record JoinRequest(String handle, String firstName, String sharedContext, boolean fromYourHomeRegion) {
	}

	public record RealtimeRoomMessage(String planId, RoomMessage message) {
	}

	public record RoomMessage(String id, String firstName, boolean mine, String body, Instant sentAt) {

		static RoomMessage of(PlanMessage m, String viewerId, String firstName) {
			return new RoomMessage(m.getId(), firstName, m.getSenderId().equals(viewerId), m.getBody(), m.getCreatedAt());
		}
	}
}
