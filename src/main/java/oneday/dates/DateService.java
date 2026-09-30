package oneday.dates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.dates.MeetingPointService.MeetingPointView;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
import oneday.identity.UserGuard;
import oneday.notify.NotificationService;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;
import oneday.sms.PhoneNumbers;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Date Mode (blueprint v2 §5.3, v1.5): the safety layer for two Connections who agreed to meet.
 *
 * <p>
 * Exact location is the most sensitive thing the product ever handles, so it is fenced on every side: it
 * flows only between the two people on a confirmed plan, only when <em>both</em> switched sharing on, only
 * inside the plan's time box, as one current point per person (never a trail), and it is deleted when the
 * plan ends. Each person may also give one trusted contact a link that follows <em>their own</em> side of the
 * plan. Help requests go to that contact and to Trust &amp; Safety, never to the other person on the date.
 */
@Service
public class DateService {

	static final Set<DatePlan.Status> OPEN = EnumSet.of(DatePlan.Status.PROPOSED, DatePlan.Status.CONFIRMED);

	private static final Set<DatePlan.Status> CLOSED = EnumSet.complementOf(EnumSet.copyOf(OPEN));

	private static final Duration RECENT = Duration.ofDays(7);

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

	private final DatePlanRepository plans;

	private final DateParticipantRepository participants;

	private final MeetingPointService meetingPoints;

	private final ConnectionService connections;

	private final ProfileService profiles;

	private final UserGuard guard;

	private final BlockChecker blocks;

	private final NotificationService notifications;

	private final EventPublisher events;

	private final TrustedContactMessenger sms;

	private final RateLimiter rateLimiter;

	private final MeterRegistry metrics;

	private final DateProperties settings;

	private final Clock clock;

	public DateService(DatePlanRepository plans, DateParticipantRepository participants,
			MeetingPointService meetingPoints, ConnectionService connections, ProfileService profiles, UserGuard guard,
			BlockChecker blocks, NotificationService notifications, EventPublisher events, TrustedContactMessenger sms, RateLimiter rateLimiter,
			MeterRegistry metrics, DateProperties settings, Clock clock) {
		this.plans = plans;
		this.participants = participants;
		this.meetingPoints = meetingPoints;
		this.connections = connections;
		this.profiles = profiles;
		this.guard = guard;
		this.blocks = blocks;
		this.notifications = notifications;
		this.events = events;
		this.sms = sms;
		this.rateLimiter = rateLimiter;
		this.metrics = metrics;
		this.settings = settings;
		this.clock = clock;
	}

	// ---- planning ---------------------------------------------------------------------------------------

	/** Proposes a meet-up inside an active Connection. At most one open plan per Connection. */
	@Transactional
	public DateView propose(String userId, String connectionId, String meetingPointId, String placeName,
			Instant startsAt, Instant endsAt) {
		guard.requireContactAllowed(userId);
		Connection connection = requireLiveConnection(userId, connectionId);
		String partnerId = connection.otherThan(userId);
		Instant now = clock.instant();
		if (startsAt == null || endsAt == null || startsAt.isBefore(now.minus(Duration.ofMinutes(5)))
				|| startsAt.isAfter(now.plus(settings.maxAdvance()))) {
			throw ApiException.unprocessable("INVALID_START", "Pick a start time between now and "
					+ settings.maxAdvance().toDays() + " days from now");
		}
		Duration length = Duration.between(startsAt, endsAt);
		if (length.compareTo(settings.minDuration()) < 0 || length.compareTo(settings.maxDuration()) > 0) {
			throw ApiException.unprocessable("INVALID_DURATION", "A plan lasts between "
					+ settings.minDuration().toMinutes() + " minutes and " + settings.maxDuration().toHours() + " hours");
		}
		String place;
		String pointId = null;
		if (meetingPointId != null && !meetingPointId.isBlank()) {
			MeetingPoint point = meetingPoints.findActive(meetingPointId.strip())
				.orElseThrow(() -> ApiException.notFound("Meeting point"));
			place = point.getName();
			pointId = point.getId();
		}
		else if (placeName != null && placeName.strip().length() >= 3) {
			place = placeName.strip();
		}
		else {
			throw ApiException.badRequest("PLACE_REQUIRED", "Pick a Meeting Point or name a public place");
		}
		if (plans.existsByConnectionIdAndStatusIn(connectionId, OPEN)) {
			throw ApiException.conflict("PLAN_ALREADY_OPEN", "You already have a plan together. Change or cancel it first.");
		}
		DatePlan plan = plans.save(new DatePlan(connectionId, userId, partnerId, pointId, place, startsAt, endsAt, now));
		Instant checkIn = defaultCheckIn(plan);
		participants.save(new DateParticipant(plan.getId(), userId, checkIn));
		participants.save(new DateParticipant(plan.getId(), partnerId, checkIn));
		notifications.pushToUser(partnerId, "New plan", "You have a new plan to look at",
				Map.of("open", "dates", "dateId", plan.getId()));
		metrics.counter("oneday.dates.proposed").increment();
		return view(plan, userId);
	}

	@Transactional
	public DateView accept(String userId, String dateId) {
		guard.requireContactAllowed(userId);
		DatePlan plan = requireLive(userId, dateId);
		if (plan.getStatus() != DatePlan.Status.PROPOSED || !plan.getPartnerId().equals(userId)) {
			throw ApiException.conflict("NOT_ACCEPTABLE", "Only the invited person can accept a proposed plan");
		}
		if (!clock.instant().isBefore(plan.getEndsAt())) {
			throw ApiException.conflict("PLAN_OVER", "This plan's time has passed");
		}
		plan.confirm(clock.instant());
		events.publish(new DomainEvent.DateConfirmed(plan.getId(), plan.getProposerId(), plan.getPartnerId(),
				plan.getStartsAt()));
		notifications.pushToUser(plan.getProposerId(), "Plan confirmed", "Your plan is on",
				Map.of("open", "dates", "dateId", plan.getId()));
		metrics.counter("oneday.dates.confirmed").increment();
		return view(plan, userId);
	}

	@Transactional
	public DateView decline(String userId, String dateId) {
		DatePlan plan = requireMember(userId, dateId);
		if (plan.getStatus() != DatePlan.Status.PROPOSED || !plan.getPartnerId().equals(userId)) {
			throw ApiException.conflict("NOT_DECLINABLE", "Only the invited person can decline a proposed plan");
		}
		close(plan, DatePlan.Status.DECLINED);
		return view(plan, userId);
	}

	/** Either person, any time before the plan is over. The other person just sees it cancelled. */
	@Transactional
	public DateView cancel(String userId, String dateId) {
		DatePlan plan = requireMember(userId, dateId);
		if (!plan.getStatus().isOpen()) {
			throw ApiException.conflict("PLAN_CLOSED", "This plan is already closed");
		}
		close(plan, DatePlan.Status.CANCELLED);
		return view(plan, userId);
	}

	/**
	 * End-of-date confirmation: "I'm home safe". Ends a confirmed plan for both people (exact location
	 * stops at once) and marks the caller safe on their trusted contact's page.
	 */
	@Transactional
	public DateView end(String userId, String dateId) {
		DatePlan plan = requireMember(userId, dateId);
		if (plan.getStatus() == DatePlan.Status.CONFIRMED) {
			close(plan, DatePlan.Status.ENDED);
		}
		else if (plan.getStatus() != DatePlan.Status.ENDED) {
			throw ApiException.conflict("PLAN_NOT_CONFIRMED", "Only a confirmed plan can be ended");
		}
		participant(plan, userId).homeSafe(clock.instant());
		return view(plan, userId);
	}

	@Transactional(readOnly = true)
	public DateView get(String userId, String dateId) {
		return view(requireMember(userId, dateId), userId);
	}

	/** Open plans and those closed in the last week, soonest first. */
	@Transactional(readOnly = true)
	public List<DateView> list(String userId) {
		guard.requireExisting(userId);
		return plans.findForUser(userId, OPEN, clock.instant().minus(RECENT))
			.stream()
			.map(p -> view(p, userId))
			.toList();
	}

	// ---- exact location, time-boxed ------------------------------------------------------------------

	@Transactional
	public DateView setLocationSharing(String userId, String dateId, boolean enabled) {
		DatePlan plan = requireLive(userId, dateId);
		if (enabled && plan.getStatus() != DatePlan.Status.CONFIRMED) {
			throw ApiException.conflict("PLAN_NOT_CONFIRMED", "Location sharing starts once you've both confirmed");
		}
		participant(plan, userId).setShareLocation(enabled);
		return view(plan, userId);
	}

	/** One current point for this person on this plan, overwritten each time. Raw values are never logged. */
	@Transactional
	public DateView updateLocation(String userId, String dateId, double lat, double lon) {
		DatePlan plan = requireLive(userId, dateId);
		Instant now = clock.instant();
		if (!plan.locationWindowOpen(now, settings.locationLead())) {
			throw ApiException.conflict("OUTSIDE_TIME_BOX",
					"Live location only works from shortly before your plan until it ends");
		}
		DateParticipant me = participant(plan, userId);
		if (!me.isShareLocation()) {
			throw ApiException.conflict("SHARING_OFF", "Switch on location sharing for this plan first");
		}
		if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
			throw ApiException.badRequest("INVALID_COORDINATES", "Latitude or longitude out of range");
		}
		if (!rateLimiter.tryAcquire("date-location:" + userId, 240, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("LOCATION_UPDATE_TOO_FREQUENT", "Location updates are rate-limited");
		}
		me.moveTo(lat, lon, now);
		return view(plan, userId);
	}

	// ---- trusted contact ----------------------------------------------------------------------------

	/**
	 * Sets (or replaces) the caller's trusted contact for this plan and texts them a private link that follows
	 * the caller's side of the plan until it is over. Replacing the contact invalidates the old link.
	 */
	@Transactional
	public TrustedContactView setTrustedContact(String userId, String dateId, String name, String rawPhone) {
		DatePlan plan = requireLive(userId, dateId);
		if (!plan.getStatus().isOpen() || !clock.instant().isBefore(plan.getEndsAt())) {
			throw ApiException.conflict("PLAN_CLOSED", "This plan is already over");
		}
		String phone = PhoneNumbers.normalize(rawPhone, settings.defaultCountryCode());
		String contactName = name == null || name.isBlank() ? "your contact" : name.strip();
		String token = newToken();
		participant(plan, userId).setTrustedContact(contactName, phone, sha256(token));
		String url = shareUrl(token);
		Profile me = profiles.require(userId);
		String when = WHEN.format(plan.getStartsAt().atZone(ZoneId.of(me.getTimeZone())));
		boolean texted = sms.text(phone, me.firstName() + " added you as their trusted contact for a meet-up on OneDay: "
				+ plan.getPlaceName() + ", " + when + ". Follow along until it ends: " + url
				+ " If you are worried and can't reach them, call " + settings.emergencyNumber() + ".");
		return new TrustedContactView(contactName, maskPhone(phone), url, texted);
	}

	@Transactional
	public void removeTrustedContact(String userId, String dateId) {
		participant(requireMember(userId, dateId), userId).setTrustedContact(null, null, null);
	}

	/**
	 * The trusted contact's page. It follows only the person who shared it: their live position if they are
	 * sharing, their check-ins and whether they asked for help. Gone once the plan and its after-care are over.
	 */
	@Transactional(readOnly = true)
	public SharedPlanView shared(String token) {
		if (token == null || token.length() < 20) {
			throw ApiException.notFound("Plan");
		}
		DateParticipant sharer = participants.findByShareTokenHash(sha256(token))
			.orElseThrow(() -> ApiException.notFound("Plan"));
		DatePlan plan = plans.findById(sharer.getDateId()).orElseThrow(() -> ApiException.notFound("Plan"));
		Instant now = clock.instant();
		boolean live = plan.getStatus().isOpen() || plan.inCareWindow(now, settings.locationLead(), settings.afterCareWindow())
				|| sharer.hasOpenEscalation();
		if (!live) {
			throw ApiException.notFound("Plan");
		}
		String sharerName = profiles.find(sharer.getUserId()).map(Profile::firstName).orElse("Your friend");
		String partnerName = profiles.find(plan.otherThan(sharer.getUserId())).map(Profile::firstName).orElse(null);
		LivePosition position = sharer.hasFreshLocation(now, settings.locationFreshness())
				? new LivePosition(sharer.getLat(), sharer.getLon(), sharer.getLocationAt()) : null;
		MeetingPointView point = meetingPoints.find(plan.getMeetingPointId()).map(p -> MeetingPointView.of(p, null)).orElse(null);
		return new SharedPlanView(sharerName, partnerName, plan.getPlaceName(), point, plan.getStartsAt(),
				plan.getEndsAt(), plan.getStatus(), position, sharer.getCheckedInAt(),
				sharer.hasOpenEscalation() ? sharer.getEscalationReason() : null, sharer.getHomeSafeAt() != null,
				settings.emergencyNumber());
	}

	// ---- check-ins and help -------------------------------------------------------------------------

	/** Moves the caller's own "Going OK?" time (inside the plan). */
	@Transactional
	public DateView scheduleCheckIn(String userId, String dateId, Instant at) {
		DatePlan plan = requireLive(userId, dateId);
		if (!plan.getStatus().isOpen() || at == null || at.isBefore(plan.getStartsAt()) || at.isAfter(plan.getEndsAt())) {
			throw ApiException.unprocessable("INVALID_CHECK_IN", "Pick a check-in time during the plan");
		}
		participant(plan, userId).setCheckInDueAt(at);
		return view(plan, userId);
	}

	/** Answers "Going OK?". "I need help" escalates exactly like SOS. */
	@Transactional
	public CheckInView checkIn(String userId, String dateId, boolean needHelp) {
		DatePlan plan = requireMember(userId, dateId);
		DateParticipant me = participant(plan, userId);
		if (needHelp) {
			return new CheckInView(false, escalate(plan, me, EscalationReason.HELP_REQUESTED), settings.emergencyNumber(),
					"Help is on the way to your trusted contact. If you're in danger, call "
							+ settings.emergencyNumber() + " now.");
		}
		me.checkedIn(clock.instant());
		return new CheckInView(true, false, settings.emergencyNumber(), "Glad it's going well. Enjoy!");
	}

	/**
	 * One-tap SOS, alongside the phone dialling 112 itself. Works from shortly before the plan until the
	 * after-care window closes, and even if the other person blocked the caller.
	 */
	@Transactional
	public CheckInView sos(String userId, String dateId) {
		DatePlan plan = requireMember(userId, dateId);
		if (!plan.inCareWindow(clock.instant(), settings.locationLead(), settings.afterCareWindow())) {
			throw ApiException.conflict("OUTSIDE_TIME_BOX", "Call " + settings.emergencyNumber() + " if you need help");
		}
		boolean alerted = escalate(plan, participant(plan, userId), EscalationReason.SOS);
		return new CheckInView(false, alerted, settings.emergencyNumber(),
				"Call " + settings.emergencyNumber() + " now if you're in danger. We've alerted your trusted contact"
						+ " and our safety team.");
	}

	// ---- Mutual Debrief -----------------------------------------------------------------------------

	@Transactional
	public DebriefView debrief(String userId, String dateId, Set<DebriefAnswer> answers) {
		DatePlan plan = requireMember(userId, dateId);
		if (!plan.isCompleted()) {
			throw ApiException.conflict("DEBRIEF_NOT_OPEN", "The debrief opens after a plan you went to");
		}
		DateParticipant me = participant(plan, userId);
		if (me.getDebriefAt() != null) {
			throw ApiException.conflict("DEBRIEF_DONE", "You've already shared your debrief");
		}
		Set<DebriefAnswer> given = EnumSet.noneOf(DebriefAnswer.class);
		if (answers != null) {
			given.addAll(answers);
		}
		me.debrief(given, clock.instant());
		if (!given.contains(DebriefAnswer.FELT_SAFE) || !given.contains(DebriefAnswer.FELT_RESPECTED)) {
			metrics.counter("oneday.dates.debrief_safety_flag").increment();
		}
		return debriefView(plan, userId);
	}

	/**
	 * Only overlapping positive answers are revealed, and only once both people answered, so nobody learns
	 * the other's lukewarm answer. Safety answers are never revealed.
	 */
	@Transactional(readOnly = true)
	public DebriefView debriefView(String userId, String dateId) {
		return debriefView(requireMember(userId, dateId), userId);
	}

	private DebriefView debriefView(DatePlan plan, String userId) {
		DateParticipant me = participant(plan, userId);
		DateParticipant them = participant(plan, plan.otherThan(userId));
		String followUp = me.getDebriefAt() != null && !(me.debriefAnswers().contains(DebriefAnswer.FELT_SAFE)
				&& me.debriefAnswers().contains(DebriefAnswer.FELT_RESPECTED))
						? "If anything felt off, you can tell our safety team privately. They will never know."
						: null;
		if (me.getDebriefAt() == null || them.getDebriefAt() == null) {
			return new DebriefView(me.getDebriefAt() != null, false, List.of(),
					me.getDebriefAt() == null ? "How did it go? Only what you both felt is ever shown."
							: "We'll show what you both felt once they've answered too.",
					followUp);
		}
		Set<DebriefAnswer> overlap = EnumSet.noneOf(DebriefAnswer.class);
		overlap.addAll(me.debriefAnswers());
		overlap.retainAll(them.debriefAnswers());
		List<String> lines = overlap.stream().filter(DebriefAnswer::shared).map(DebriefAnswer::overlapLine).toList();
		return new DebriefView(true, true, lines,
				lines.isEmpty() ? "Thanks for sharing. Every meet-up teaches you something." : "Here's what you both felt ✨",
				followUp);
	}

	// ---- scheduler work (see DateModeScheduler) --------------------------------------------------------

	/** Sends "Going OK?" to everyone whose check-in time has come. */
	@Transactional
	public int promptDueCheckIns() {
		Instant now = clock.instant();
		List<DateParticipant> due = participants.findCheckInsDue(now);
		for (DateParticipant p : due) {
			p.prompted(now);
			notifications.pushToUser(p.getUserId(), "Going OK?", "Tap to check in",
					Map.of("open", "dates", "dateId", p.getDateId(), "action", "check-in"));
		}
		return due.size();
	}

	/** An unanswered "Going OK?" alerts the person's trusted contact (only people who chose one are asked). */
	@Transactional
	public int escalateMissedCheckIns() {
		List<DateParticipant> missed = participants.findCheckInsMissed(clock.instant().minus(settings.checkInGrace()),
				EnumSet.of(DatePlan.Status.CONFIRMED, DatePlan.Status.ENDED));
		int escalated = 0;
		for (DateParticipant p : missed) {
			DatePlan plan = plans.findById(p.getDateId()).orElseThrow();
			if (escalate(plan, p, EscalationReason.MISSED_CHECK_IN)) {
				escalated++;
			}
		}
		return escalated;
	}

	/** Closes plans whose time passed: confirmed ones become ENDED (completed), unanswered proposals EXPIRED. */
	@Transactional
	public int closeFinishedPlans() {
		Instant now = clock.instant();
		int closed = 0;
		for (DatePlan plan : plans.findByStatusAndEndsAtLessThanEqual(DatePlan.Status.CONFIRMED, now)) {
			close(plan, DatePlan.Status.ENDED);
			closed++;
		}
		for (DatePlan plan : plans.findByStatusAndStartsAtLessThanEqual(DatePlan.Status.PROPOSED, now)) {
			close(plan, DatePlan.Status.EXPIRED);
			closed++;
		}
		return closed;
	}

	/**
	 * Deletes trusted-contact details and share links once the after-care window is over. A person with an
	 * open escalation keeps theirs (evidence for Trust &amp; Safety) until it is resolved, at most
	 * {@code escalationRetention}.
	 */
	@Transactional
	public int purgeClosedPlans() {
		Instant now = clock.instant();
		int purged = 0;
		for (DatePlan plan : plans.findByStatusInAndClosedAtLessThanEqual(CLOSED, now.minus(settings.afterCareWindow()))) {
			for (DateParticipant p : participants.findByKeyDateId(plan.getId())) {
				boolean held = p.hasOpenEscalation() && now.isBefore(p.getEscalatedAt().plus(settings.escalationRetention()));
				if (p.getPurgedAt() == null && !held) {
					p.purge(now);
					purged++;
				}
			}
		}
		return purged;
	}

	/** A block ends every open plan between the two people at once (the block itself is never revealed). */
	@Transactional
	public void cancelBetween(String a, String b) {
		plans.findOpenBetween(a, b, OPEN).forEach(p -> close(p, DatePlan.Status.CANCELLED));
	}

	/** The account is going away (deferred erasure): its open plans are cancelled for the other person. */
	@Transactional
	public void cancelAllFor(String userId) {
		plans.findAllInvolving(userId).stream().filter(p -> p.getStatus().isOpen())
			.forEach(p -> close(p, DatePlan.Status.CANCELLED));
	}

	// ---- Trust & Safety ------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DateParticipant> openEscalations() {
		return participants.findOpenEscalations();
	}

	@Transactional(readOnly = true)
	public Optional<DatePlan> find(String dateId) {
		return plans.findById(dateId);
	}

	@Transactional
	public DateParticipant resolveEscalation(String dateId, String userId, String staffId) {
		DateParticipant p = participants.findById(new DateParticipant.Key(dateId, userId))
			.filter(DateParticipant::hasOpenEscalation)
			.orElseThrow(() -> ApiException.notFound("Open alert"));
		p.resolveEscalation(staffId, clock.instant());
		plans.findById(dateId).filter(plan -> !plan.getStatus().isOpen()).ifPresent(plan -> p.purge(clock.instant()));
		return p;
	}

	@Transactional(readOnly = true)
	public Optional<DateParticipant> participantOf(String dateId, String userId) {
		return participants.findById(new DateParticipant.Key(dateId, userId));
	}

	// ---- data rights ---------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<Map<String, Object>> export(String userId) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (DatePlan plan : plans.findAllInvolving(userId)) {
			DateParticipant me = participants.findById(new DateParticipant.Key(plan.getId(), userId)).orElse(null);
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("place", plan.getPlaceName());
			row.put("startsAt", plan.getStartsAt());
			row.put("endsAt", plan.getEndsAt());
			row.put("status", plan.getStatus());
			row.put("youProposed", plan.getProposerId().equals(userId));
			if (me != null) {
				row.put("trustedContact", me.getContactName());
				row.put("checkedInAt", me.getCheckedInAt());
				row.put("askedForHelpAt", me.getEscalatedAt());
				row.put("debrief", me.debriefAnswers());
			}
			rows.add(row);
		}
		return rows;
	}

	/** Erasure: plans the user was part of are removed for both people, with every participant row. */
	@Transactional
	public void forget(String userId) {
		List<DatePlan> mine = plans.findAllInvolving(userId);
		if (mine.isEmpty()) {
			return;
		}
		participants.deleteByDateIds(mine.stream().map(DatePlan::getId).toList());
		plans.deleteAll(mine);
	}

	// ---- internals -----------------------------------------------------------------------------------

	private boolean escalate(DatePlan plan, DateParticipant who, EscalationReason reason) {
		if (!who.escalate(reason, clock.instant())) {
			return who.hasTrustedContact();
		}
		events.publish(new DomainEvent.DateSafetyEscalated(plan.getId(), who.getUserId(), reason.name()));
		metrics.counter("oneday.dates.escalations", "reason", reason.name()).increment();
		return who.hasTrustedContact();
	}

	private void close(DatePlan plan, DatePlan.Status to) {
		boolean completed = plan.close(to, clock.instant());
		// Exact location stops the moment a plan closes, for both people; contact links live on for after-care.
		participants.findByKeyDateId(plan.getId()).forEach(p -> p.setShareLocation(false));
		if (completed) {
			events.publish(new DomainEvent.DateCompleted(plan.getId(), plan.getProposerId(), plan.getPartnerId()));
			metrics.counter("oneday.dates.completed").increment();
		}
	}

	private Instant defaultCheckIn(DatePlan plan) {
		Instant due = plan.getStartsAt().plus(settings.checkInAfter());
		return due.isAfter(plan.getEndsAt()) ? plan.getEndsAt() : due;
	}

	private Connection requireLiveConnection(String userId, String connectionId) {
		Connection connection = connections.requireMember(connectionId, userId);
		if (!connection.isActive() || blocks.isBlockedEitherWay(userId, connection.otherThan(userId))) {
			throw ApiException.conflict("CONNECTION_INACTIVE", "This connection is no longer active");
		}
		return connection;
	}

	private DatePlan requireMember(String userId, String dateId) {
		guard.requireExisting(userId);
		return plans.findById(dateId).filter(p -> p.involves(userId)).orElseThrow(() -> ApiException.notFound("Plan"));
	}

	/** A plan whose two people are still connected and not blocked; anything else behaves as cancelled. */
	private DatePlan requireLive(String userId, String dateId) {
		DatePlan plan = requireMember(userId, dateId);
		if (!connections.areConnected(userId, plan.otherThan(userId))
				|| blocks.isBlockedEitherWay(userId, plan.otherThan(userId))) {
			throw ApiException.conflict("CONNECTION_INACTIVE", "This plan is no longer active");
		}
		return plan;
	}

	private DateParticipant participant(DatePlan plan, String userId) {
		return participants.findById(new DateParticipant.Key(plan.getId(), userId))
			.orElseThrow(() -> ApiException.notFound("Plan"));
	}

	private DateView view(DatePlan plan, String userId) {
		Instant now = clock.instant();
		Map<String, DateParticipant> sides = participants.findByKeyDateId(plan.getId())
			.stream()
			.collect(Collectors.toMap(DateParticipant::getUserId, Function.identity()));
		DateParticipant me = sides.get(userId);
		DateParticipant them = sides.get(plan.otherThan(userId));
		String partnerId = plan.otherThan(userId);
		boolean stillTogether = connections.areConnected(userId, partnerId) && !blocks.isBlockedEitherWay(userId, partnerId);
		boolean window = stillTogether && plan.locationWindowOpen(now, settings.locationLead());
		// Exact location only when both switched it on (mutual consent) inside the time box.
		LivePosition partnerPosition = window && me != null && me.isShareLocation() && them != null
				&& them.hasFreshLocation(now, settings.locationFreshness())
						? new LivePosition(them.getLat(), them.getLon(), them.getLocationAt()) : null;
		DatePlan.Status status = stillTogether || !plan.getStatus().isOpen() ? plan.getStatus() : DatePlan.Status.CANCELLED;
		MeetingPointView point = meetingPoints.find(plan.getMeetingPointId()).map(p -> MeetingPointView.of(p, null)).orElse(null);
		YourSide you = me == null ? null
				: new YourSide(me.isShareLocation(), me.getCheckInDueAt(), me.getCheckedInAt(), me.getContactName(),
						me.getHomeSafeAt() != null, me.getDebriefAt() != null);
		return new DateView(plan.getId(), plan.getConnectionId(),
				profiles.find(partnerId).map(Profile::firstName).orElse(null), plan.getPlaceName(), point,
				plan.getStartsAt(), plan.getEndsAt(), status, plan.getProposerId().equals(userId), window, you,
				them != null && them.isShareLocation() && window, partnerPosition, settings.emergencyNumber());
	}

	private String shareUrl(String token) {
		String base = settings.publicBaseUrl().endsWith("/") ? settings.publicBaseUrl() : settings.publicBaseUrl() + "/";
		return base + "date-share/" + token;
	}

	private static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String maskPhone(String e164) {
		return e164.length() <= 4 ? "****" : "•••• " + e164.substring(e164.length() - 4);
	}

	// ---- views ---------------------------------------------------------------------------------------

	/**
	 * The caller's view of a plan. {@code partnerSharing} and {@code partnerLocation} appear only inside the
	 * time box; the location only while both people share.
	 */
	public record DateView(String id, String connectionId, String partnerFirstName, String placeName,
			MeetingPointView meetingPoint, Instant startsAt, Instant endsAt, DatePlan.Status status, boolean youProposed,
			boolean locationWindowOpen, YourSide you, boolean partnerSharing, LivePosition partnerLocation,
			String emergencyNumber) {
	}

	public record YourSide(boolean sharingLocation, Instant checkInAt, Instant checkedInAt, String trustedContact,
			boolean homeSafe, boolean debriefSubmitted) {
	}

	public record LivePosition(double lat, double lon, Instant at) {
	}

	/** {@code texted} is false when no SMS provider is configured: share {@code shareUrl} yourself. */
	public record TrustedContactView(String name, String phone, String shareUrl, boolean texted) {
	}

	public record SharedPlanView(String firstName, String meetingWith, String placeName, MeetingPointView meetingPoint,
			Instant startsAt, Instant endsAt, DatePlan.Status status, LivePosition location, Instant lastCheckIn,
			EscalationReason alert, boolean homeSafe, String emergencyNumber) {
	}

	public record CheckInView(boolean ok, boolean trustedContactAlerted, String emergencyNumber, String message) {
	}

	public record DebriefView(boolean youAnswered, boolean revealed, List<String> bothFelt, String message,
			String privateSafetyFollowUp) {
	}
}
