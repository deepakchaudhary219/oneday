package oneday.signals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import oneday.chat.ChatService;
import oneday.chat.Conversation;
import oneday.common.ApiException;
import oneday.common.ProductMetrics;
import oneday.config.OneDayProperties;
import oneday.connections.Connection;
import oneday.connections.ConnectionOrigin;
import oneday.connections.ConnectionService;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
import oneday.identity.UserGuard;
import oneday.moments.Moment;
import oneday.moments.MomentService;
import oneday.profile.ActivityTags;
import oneday.profile.Affinity;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signals, the Signal Budget and the Reaction Window (blueprint v2 §4).
 *
 * <p>
 * The design shifts the cost of an unwanted approach onto the sender: a limited daily budget, no free
 * text, one signal per moment, and no feedback about whether a signal was seen, passed or expired.
 * The recipient reviews signals in a bounded digest on her own schedule; nothing interrupts her.
 */
@Service
public class SignalService {

	private static final Duration BUDGET_WINDOW = Duration.ofHours(24);

	private final SignalRepository signals;

	private final MomentService moments;

	private final ProfileService profiles;

	private final ConnectionService connections;

	private final ChatService chat;

	private final UserGuard guard;

	private final BlockChecker blocks;

	private final Clock clock;

	private final OneDayProperties.Signals settings;

	private final ProductMetrics metrics;

	private final EventPublisher events;

	public SignalService(SignalRepository signals, MomentService moments, ProfileService profiles,
			ConnectionService connections, ChatService chat, UserGuard guard, BlockChecker blocks, Clock clock,
			OneDayProperties properties, ProductMetrics metrics, EventPublisher events) {
		this.metrics = metrics;
		this.events = events;
		this.signals = signals;
		this.moments = moments;
		this.profiles = profiles;
		this.connections = connections;
		this.chat = chat;
		this.guard = guard;
		this.blocks = blocks;
		this.clock = clock;
		this.settings = properties.signals();
	}

	@Transactional
	public SentSignalView send(String senderId, String momentId, Reaction reaction, String rawActivityRef) {
		guard.requireContactAllowed(senderId);
		Moment moment = moments.requireLivePublic(momentId);
		String recipientId = moment.getOwnerId();
		if (recipientId.equals(senderId)) {
			throw ApiException.unprocessable("CANNOT_SIGNAL_SELF", "That's your own moment");
		}
		if (blocks.isBlockedEitherWay(senderId, recipientId) || guard.reachableAmong(List.of(recipientId)).isEmpty()
				|| !connections.inCouple(List.of(senderId, recipientId)).isEmpty()) {
			throw ApiException.notFound("Moment");
		}
		if (connections.areConnected(senderId, recipientId)) {
			throw ApiException.conflict("ALREADY_CONNECTED", "You're already connected. Just send them a message.");
		}
		Instant now = clock.instant();
		// One approach per person per Reaction Window, whatever its fate: a pass is never answered by a
		// "reminder" through their next story (blueprint §37.3). The reply is identical for every outcome.
		if (signals.existsBySenderIdAndMomentId(senderId, momentId) || signals
			.existsBySenderIdAndRecipientIdAndCreatedAtAfter(senderId, recipientId, now.minus(settings.reactionWindow()))) {
			throw ApiException.conflict("ALREADY_SIGNALLED", "You've already sent them a signal. Give it time.");
		}
		String activityRef = ActivityTags.normalize(rawActivityRef);
		if (activityRef != null && !activityRef.equals(moment.getActivityTag())
				&& !profiles.require(recipientId).getActivities().contains(activityRef)) {
			throw ApiException.unprocessable("INVALID_ACTIVITY_REF", "Pick one of the activities they're up for");
		}
		if (signals.countBySenderIdAndCreatedAtAfter(senderId, now.minus(BUDGET_WINDOW)) >= settings.dailyBudget()) {
			throw ApiException.tooManyRequests("SIGNAL_BUDGET_EXHAUSTED",
					"You've used today's signals. Thoughtful beats many; more tomorrow.");
		}
		Instant windowEnd = moment.getCreatedAt().plus(settings.reactionWindow());
		Signal signal = signals
			.save(new Signal(senderId, recipientId, momentId, reaction, activityRef, now, windowEnd));
		metrics.signalSent();
		events.publish(new DomainEvent.SignalSent(signal.getId(), senderId, recipientId));
		return SentSignalView.of(signal);
	}

	/** A bounded batch of in-window signals, best shared context first. No expiry times are exposed. */
	@Transactional(readOnly = true)
	public DigestView digest(String recipientId) {
		Instant now = clock.instant();
		Set<String> blocked = blocks.blockedEitherWay(recipientId);
		List<Signal> pending = signals
			.findByRecipientIdAndStatusAndWindowExpiresAtAfter(recipientId, Signal.Status.PENDING, now)
			.stream()
			.filter(s -> !blocked.contains(s.getSenderId()))
			.toList();
		Set<String> reachable = guard.reachableAmong(pending.stream().map(Signal::getSenderId).toList());
		Profile me = profiles.require(recipientId);
		Map<String, Profile> senders = pending.stream()
			.map(Signal::getSenderId)
			.filter(reachable::contains)
			.distinct()
			.collect(Collectors.toMap(Function.identity(), profiles::require));

		List<DigestItem> items = pending.stream()
			.filter(s -> senders.containsKey(s.getSenderId()))
			.map(s -> {
				Profile sender = senders.get(s.getSenderId());
				String aboutActivity = moments.find(s.getMomentId()).map(Moment::getActivityTag).orElse(null);
				Affinity affinity = Affinity.between(me, sender);
				return new Ranked(new DigestItem(s.getId(), sender.firstName(), s.getReaction(),
						s.getReaction().label(), s.getActivityRef(), aboutActivity,
						affinity.explanation(aboutActivity), affinity.rootsMatch(), s.getCreatedAt()), affinity.rank());
			})
			.sorted(Comparator.comparingInt(Ranked::rank)
				.reversed()
				.thenComparing(r -> r.item().receivedAt()))
			.map(Ranked::item)
			.toList();
		int batch = settings.digestBatchSize();
		boolean caughtUp = items.size() <= batch;
		return new DigestView(items.stream().limit(batch).toList(), caughtUp,
				caughtUp ? "You're caught up for now." : "More signals will be here next time you check in.");
	}

	/** Mutual Reveal (Layer 2): creates the Connection and a Conversation seeded with shared context. */
	@Transactional
	public RevealView reveal(String recipientId, String signalId) {
		guard.requireContactAllowed(recipientId);
		Signal signal = requireActionableFor(recipientId, signalId);
		String senderId = signal.getSenderId();
		if (blocks.isBlockedEitherWay(senderId, recipientId) || guard.reachableAmong(List.of(senderId)).isEmpty()) {
			throw ApiException.notFound("Signal");
		}
		Instant now = clock.instant();
		Connection connection = connections.connect(senderId, recipientId, ConnectionOrigin.MUTUAL_REVEAL);
		String seed = signal.getActivityRef() != null ? "Connected over: " + signal.getActivityRef()
				: "Connected over your moment (" + signal.getReaction().label() + ")";
		Conversation conversation = chat.openFor(connection, seed);
		signal.resolve(Signal.Status.REVEALED, now);
		signals.archivePendingBetween(senderId, recipientId, now);
		metrics.mutualReveal();
		events.publish(new DomainEvent.MutualRevealed(connection.getId(), connection.getUserA(), connection.getUserB()));
		return new RevealView(connection.getId(), conversation.getId(), conversation.getSeedContext());
	}

	/** Silent pass: the sender is never told. */
	@Transactional
	public void pass(String recipientId, String signalId) {
		requireActionableFor(recipientId, signalId).resolve(Signal.Status.PASSED, clock.instant());
	}

	/**
	 * Consent Trail (blueprint §35.3): what I sent and when. Passed, archived and unseen are
	 * indistinguishable by design; only a signal that became a Connection is marked.
	 */
	@Transactional(readOnly = true)
	public List<SentSignalView> sentBy(String senderId) {
		return signals.findBySenderIdOrderByCreatedAtDesc(senderId, PageRequest.of(0, 100))
			.stream()
			.map(SentSignalView::of)
			.toList();
	}

	/** People this user approached within the current Reaction Window; discovery hides them meanwhile. */
	@Transactional(readOnly = true)
	public Set<String> recentlySignalledBy(String senderId) {
		return new HashSet<>(
				signals.findRecipientsSignalledSince(senderId, clock.instant().minus(settings.reactionWindow())));
	}

	@Transactional(readOnly = true)
	public long pendingCount(String recipientId) {
		return signals.countByRecipientIdAndStatusAndWindowExpiresAtAfter(recipientId, Signal.Status.PENDING,
				clock.instant());
	}

	/** The other party of a signal the user sent or received. */
	@Transactional(readOnly = true)
	public String counterpartOf(String signalId, String userId) {
		Signal signal = signals.findById(signalId)
			.filter(s -> s.getSenderId().equals(userId) || s.getRecipientId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("Signal"));
		return signal.getSenderId().equals(userId) ? signal.getRecipientId() : signal.getSenderId();
	}

	@Transactional
	public void archiveBetween(String a, String b) {
		signals.archivePendingBetween(a, b, clock.instant());
	}

	@Transactional
	public int archiveExpired() {
		return signals.archiveExpired(clock.instant());
	}

	@Transactional(readOnly = true)
	public List<Signal> allSentBy(String userId) {
		return signals.findBySenderIdOrderByCreatedAtDesc(userId);
	}

	@Transactional(readOnly = true)
	public List<Signal> allReceivedBy(String userId) {
		return signals.findByRecipientIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public void deleteInvolving(String userId) {
		signals.deleteInvolving(userId);
	}

	private Signal requireActionableFor(String recipientId, String signalId) {
		return signals.findById(signalId)
			.filter(s -> s.getRecipientId().equals(recipientId) && s.isActionable(clock.instant()))
			.orElseThrow(() -> ApiException.notFound("Signal"));
	}

	public record SentSignalView(String id, String momentId, Reaction reaction, String activityRef, Instant sentAt,
			boolean becameConnection) {

		static SentSignalView of(Signal s) {
			return new SentSignalView(s.getId(), s.getMomentId(), s.getReaction(), s.getActivityRef(),
					s.getCreatedAt(), s.getStatus() == Signal.Status.REVEALED);
		}
	}

	public record DigestItem(String signalId, String firstName, Reaction reaction, String reactionLabel,
			String activityRef, String aboutActivity, String sharedContext, boolean rootsMatch, Instant receivedAt) {
	}

	/** Internal ordering only; the rank never leaves the server. */
	private record Ranked(DigestItem item, int rank) {
	}

	public record DigestView(List<DigestItem> signals, boolean caughtUp, String message) {
	}

	public record RevealView(String connectionId, String conversationId, String seedContext) {
	}
}
