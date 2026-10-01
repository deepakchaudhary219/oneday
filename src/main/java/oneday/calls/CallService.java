package oneday.calls;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import oneday.chat.ChatService;
import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.identity.UserGuard;
import oneday.notify.NotificationService;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.realtime.RealtimeService;
import oneday.safety.BlockChecker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Layered Video (blueprint v2 §11, read in the spirit of Layered Reveal): calls between Connections that start
 * as voice and step up to blurred and then clear video only when both people are ready.
 *
 * <ul>
 * <li><b>Chat first.</b> Calls open once both people have written in the conversation.</li>
 * <li><b>Layers by consent.</b> Each person sets what they're comfortable with and the call runs at the lower
 * of the two. Stepping down is instant and needs no one's agreement. The other side only learns "they'd be
 * happy to step up", never a pending request to answer.</li>
 * <li><b>Signalling only.</b> The server relays WebRTC offers, answers and ICE candidates and issues short-lived
 * TURN credentials. Media is peer-to-peer, DTLS-SRTP encrypted, and never recorded. The client enforces the
 * layer on its own outgoing tracks (no video track below {@code BLURRED}, blur applied before encoding).</li>
 * </ul>
 */
@Service
public class CallService {

	static final Duration RING_TIMEOUT = Duration.ofSeconds(45);

	static final Duration MAX_CALL = Duration.ofHours(3);

	static final Duration RETENTION = Duration.ofDays(30);

	static final int MAX_SIGNAL = 16_384;

	private final CallRepository calls;

	private final ConnectionService connections;

	private final ChatService chat;

	private final ProfileService profiles;

	private final UserGuard guard;

	private final BlockChecker blocks;

	private final RealtimeService realtime;

	private final NotificationService notifications;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	public CallService(CallRepository calls, ConnectionService connections, ChatService chat, ProfileService profiles,
			UserGuard guard, BlockChecker blocks, RealtimeService realtime, NotificationService notifications,
			RateLimiter rateLimiter, Clock clock) {
		this.calls = calls;
		this.connections = connections;
		this.chat = chat;
		this.profiles = profiles;
		this.guard = guard;
		this.blocks = blocks;
		this.realtime = realtime;
		this.notifications = notifications;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
	}

	@Transactional
	public CallView start(String callerId, String connectionId) {
		guard.requireContactAllowed(callerId);
		Connection connection = connections.requireMember(connectionId, callerId);
		String callee = connection.otherThan(callerId);
		if (!connection.isActive() || blocks.isBlockedEitherWay(callerId, callee)) {
			throw ApiException.conflict("CONNECTION_INACTIVE", "This connection is no longer active");
		}
		if (!chat.bothHaveWritten(connection)) {
			throw ApiException.conflict("CHAT_FIRST", "Calls open up once you've both written in chat");
		}
		if (!rateLimiter.tryAcquire("call:" + callerId + ":" + connectionId, 6, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("CALL_RATE_LIMIT", "Give them a little time before calling again");
		}
		if (!calls.findOpenFor(callerId).isEmpty() || !calls.findOpenFor(callee).isEmpty()) {
			throw ApiException.conflict("BUSY", "One of you is already on a call");
		}
		Call call = calls.save(new Call(connectionId, callerId, callee, clock.instant()));
		String callerName = firstName(callerId);
		realtime.toUser(callee, "call", CallView.of(call, callee, callerName));
		notifications.requestPush(callee, callerName + " is calling", "Voice first. Video only if you both want it.",
				Map.of("type", "call", "callId", call.getId()));
		return CallView.of(call, callerId, firstName(callee));
	}

	@Transactional
	public CallView accept(String userId, String callId) {
		Call call = requireOpen(userId, callId);
		if (!call.getCalleeId().equals(userId) || call.getStatus() != Call.Status.RINGING) {
			throw ApiException.conflict("NOT_RINGING", "There's no incoming call to answer");
		}
		call.answer(clock.instant());
		return broadcast(call, userId);
	}

	@Transactional
	public CallView decline(String userId, String callId) {
		Call call = requireOpen(userId, callId);
		if (!call.getCalleeId().equals(userId) || call.getStatus() != Call.Status.RINGING) {
			throw ApiException.conflict("NOT_RINGING", "There's no incoming call to decline");
		}
		call.end(Call.EndReason.DECLINED, clock.instant());
		return broadcast(call, userId);
	}

	@Transactional
	public CallView hangUp(String userId, String callId) {
		Call call = requireOpen(userId, callId);
		call.end(Call.EndReason.HUNG_UP, clock.instant());
		return broadcast(call, userId);
	}

	/** Sets what this person is comfortable sharing. Stepping up past voice needs an answered call. */
	@Transactional
	public CallView setLayer(String userId, String callId, Layer wants) {
		if (wants == null) {
			throw ApiException.badRequest("LAYER_REQUIRED", "Choose VOICE, BLURRED or CLEAR");
		}
		Call call = requireOpen(userId, callId);
		if (call.getStatus() != Call.Status.ACTIVE && wants != Layer.VOICE) {
			throw ApiException.conflict("NOT_ACTIVE", "Video can be turned on once the call is answered");
		}
		call.setWants(userId, wants);
		return broadcast(call, userId);
	}

	/** Relays a WebRTC offer, answer or ICE candidate to the other person's open apps. */
	@Transactional(readOnly = true)
	public void signal(String userId, String callId, SignalKind kind, String payload) {
		Call call = requireOpen(userId, callId);
		if (kind == null || payload == null || payload.isEmpty() || payload.length() > MAX_SIGNAL) {
			throw ApiException.badRequest("INVALID_SIGNAL", "kind and payload (up to 16 KB) are required");
		}
		if (!rateLimiter.tryAcquire("call-signal:" + callId + ":" + userId, 300, Duration.ofMinutes(1))) {
			throw ApiException.tooManyRequests("SIGNAL_RATE_LIMIT", "Too many signalling messages");
		}
		realtime.toUser(call.otherThan(userId), "call-signal", new Signal(callId, kind, payload));
	}

	@Transactional(readOnly = true)
	public CallView get(String userId, String callId) {
		Call call = calls.findById(callId).filter(c -> c.involves(userId)).orElseThrow(() -> ApiException.notFound("Call"));
		return CallView.of(call, userId, firstName(call.otherThan(userId)));
	}

	/** The other person on a call, for blocking or reporting it. */
	@Transactional(readOnly = true)
	public Optional<String> otherOn(String viewerId, String callId) {
		return calls.findById(callId).filter(c -> c.involves(viewerId)).map(c -> c.otherThan(viewerId));
	}

	@Transactional
	public void endBetween(String a, String b, Call.EndReason reason) {
		Instant now = clock.instant();
		calls.findOpenBetween(a, b).forEach(call -> {
			call.end(reason, now);
			broadcast(call, a);
		});
	}

	/** Unanswered calls become missed; calls that run past the limit end. Correctness never waits for this. */
	@Scheduled(fixedDelayString = "${oneday.calls.sweep-interval:PT15S}", initialDelayString = "PT1M")
	@Transactional
	public int sweep() {
		Instant now = clock.instant();
		List<Call> missed = calls.findRingingSince(now.minus(RING_TIMEOUT));
		missed.forEach(call -> {
			call.end(Call.EndReason.MISSED, now);
			broadcast(call, call.getCallerId());
		});
		List<Call> tooLong = calls.findActiveSince(now.minus(MAX_CALL));
		tooLong.forEach(call -> {
			call.end(Call.EndReason.TIMED_OUT, now);
			broadcast(call, call.getCallerId());
		});
		calls.purgeEndedBefore(now.minus(RETENTION));
		return missed.size() + tooLong.size();
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> export(String userId) {
		return calls.findInvolving(userId)
			.stream()
			.map(c -> Map.<String, Object>of("direction", c.getCallerId().equals(userId) ? "OUTGOING" : "INCOMING",
					"startedAt", c.getStartedAt(), "status", c.getStatus().name(), "endReason",
					c.getEndReason() == null ? "" : c.getEndReason().name()))
			.toList();
	}

	@Transactional
	public void forget(String userId) {
		calls.deleteInvolving(userId);
	}

	private Call requireOpen(String userId, String callId) {
		Call call = calls.findById(callId).filter(c -> c.involves(userId)).orElseThrow(() -> ApiException.notFound("Call"));
		if (!call.getStatus().isOpen()) {
			throw ApiException.conflict("CALL_ENDED", "This call has ended");
		}
		return call;
	}

	/** Both people get their own view (their own "you want", the other's readiness as a hint only). */
	private CallView broadcast(Call call, String actorId) {
		String other = call.otherThan(actorId);
		realtime.toUser(other, "call", CallView.of(call, other, firstName(actorId)));
		CallView mine = CallView.of(call, actorId, firstName(other));
		realtime.toUser(actorId, "call", mine);
		return mine;
	}

	private String firstName(String userId) {
		return profiles.find(userId).map(Profile::firstName).orElse("Someone");
	}

	public enum SignalKind {
		OFFER, ANSWER, ICE
	}

	public record Signal(String callId, SignalKind kind, String payload) {
	}

	/**
	 * {@code layer} is what the call runs at; {@code youWant} is this person's own setting;
	 * {@code theyAreReadyForMore} is true when the other person would step up if this person does too.
	 */
	public record CallView(String id, String connectionId, String firstName, String direction, String status,
			String endReason, Layer layer, Layer youWant, boolean theyAreReadyForMore, Instant startedAt,
			Instant answeredAt, Instant endedAt) {

		static CallView of(Call call, String viewerId, String otherFirstName) {
			String other = call.otherThan(viewerId);
			return new CallView(call.getId(), call.getConnectionId(), otherFirstName,
					call.getCallerId().equals(viewerId) ? "OUTGOING" : "INCOMING", call.getStatus().name(),
					call.getEndReason() == null ? null : call.getEndReason().name(), call.layer(),
					call.wantsOf(viewerId), call.wantsOf(other).compareTo(call.layer()) > 0, call.getStartedAt(),
					call.getAnsweredAt(), call.getEndedAt());
		}
	}
}
