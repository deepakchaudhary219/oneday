package oneday.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import oneday.common.ApiException;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.e2ee.E2eeService;
import oneday.e2ee.E2eeService.Outgoing;
import oneday.empathy.EmpathyMirror;
import oneday.empathy.EmpathyMirror.Concern;
import oneday.identity.UserGuard;
import oneday.realtime.RealtimeService;
import oneday.safety.BlockChecker;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Friend-Mode chat (blueprint v2 §3): persistent and unrestricted between active Connections. */
@Service
public class ChatService {

	private static final Duration BALANCE_WINDOW = Duration.ofDays(7);

	private final ConversationRepository conversations;

	private final MessageRepository messages;

	private final ConnectionService connections;

	private final UserGuard guard;

	private final BlockChecker blocks;

	private final Clock clock;

	private final RealtimeService realtime;

	private final PacingGuardian pacing;

	private final EmpathyMirror empathy;

	private final E2eeService e2ee;

	public ChatService(ConversationRepository conversations, MessageRepository messages,
			ConnectionService connections, UserGuard guard, BlockChecker blocks, Clock clock, RealtimeService realtime,
			PacingGuardian pacing, EmpathyMirror empathy, E2eeService e2ee) {
		this.empathy = empathy;
		this.e2ee = e2ee;
		this.realtime = realtime;
		this.pacing = pacing;
		this.conversations = conversations;
		this.messages = messages;
		this.connections = connections;
		this.guard = guard;
		this.blocks = blocks;
		this.clock = clock;
	}

	@Transactional
	public Conversation openFor(Connection connection, String seedContext) {
		return conversations.findByConnectionId(connection.getId())
			.orElseGet(() -> conversations.save(new Conversation(connection.getId(), seedContext, clock.instant())));
	}

	@Transactional(readOnly = true)
	public Optional<Conversation> forConnection(String connectionId) {
		return conversations.findByConnectionId(connectionId);
	}

	@Transactional
	public MessageView send(String userId, String conversationId, String body, boolean sendAnyway) {
		guard.requireContactAllowed(userId);
		Conversation conversation = requireConversation(conversationId);
		Connection connection = connections.requireMember(conversation.getConnectionId(), userId);
		if (!connection.isActive() || blocks.isBlockedEitherWay(userId, connection.otherThan(userId))) {
			throw ApiException.conflict("CONVERSATION_INACTIVE", "This conversation is no longer active");
		}
		if (conversation.isE2ee()) {
			throw ApiException.conflict("E2EE_REQUIRED", "This chat is end-to-end encrypted. Update the app to send.");
		}
		int streak = pacing.beforeSend(conversationId, userId);
		var tone = empathy.reflect("chat", body, sendAnyway);
		Message message = new Message(conversationId, userId, body.strip(), clock.instant());
		tone.ifPresent(t -> message.flagTone(t.name()));
		messages.save(message);
		// Each side gets its own view (mine true/false), the sender's other devices included.
		realtime.toUser(connection.otherThan(userId), "message",
				new RealtimeMessage(conversationId, MessageView.of(message, connection.otherThan(userId))));
		realtime.toUser(userId, "message", new RealtimeMessage(conversationId, MessageView.of(message, userId)));
		return MessageView.of(message, userId).withHint(pacing.hintAfterSend(streak));
	}

	/**
	 * An end-to-end encrypted message: the server stores metadata and the franking commitment, queues one
	 * envelope per device, and never sees the text. The Empathy Mirror runs on the sender's device. The first
	 * encrypted message makes the conversation encrypted for good.
	 */
	@Transactional
	public MessageView sendEncrypted(String userId, String sessionId, String conversationId, int senderDevice,
			byte[] frankingCommitment, List<Outgoing> envelopes) {
		guard.requireContactAllowed(userId);
		Conversation conversation = requireConversation(conversationId);
		Connection connection = connections.requireMember(conversation.getConnectionId(), userId);
		String other = connection.otherThan(userId);
		if (!connection.isActive() || blocks.isBlockedEitherWay(userId, other)) {
			throw ApiException.conflict("CONVERSATION_INACTIVE", "This conversation is no longer active");
		}
		if (frankingCommitment == null || frankingCommitment.length != 32) {
			throw ApiException.badRequest("INVALID_COMMITMENT", "commitment is HMAC-SHA256: 32 bytes, base64");
		}
		int streak = pacing.beforeSend(conversationId, userId);
		Message message = messages.save(Message.encrypted(conversationId, userId, frankingCommitment, clock.instant()));
		e2ee.deliver(userId, sessionId, senderDevice, other, conversationId, message.getId(), envelopes);
		conversation.markE2ee();
		return MessageView.of(message, userId).withHint(pacing.hintAfterSend(streak));
	}

	/** The connection a reportable encrypted message belongs to, if the franking reveal matches what was sent. */
	@Transactional(readOnly = true)
	public Optional<String> verifyFranking(String userId, String conversationId, String messageId, String plaintext,
			byte[] frankingKey) {
		Conversation conversation = requireConversation(conversationId);
		connections.requireMember(conversation.getConnectionId(), userId);
		Message message = messages.findById(messageId)
			.filter(m -> m.getConversationId().equals(conversationId) && m.isEncrypted()
					&& !m.getSenderId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("Message"));
		boolean matches = Franking.verify(message.getFrankingCommitment(), frankingKey, plaintext);
		return matches ? Optional.of(conversation.getConnectionId()) : Optional.empty();
	}

	@Transactional(readOnly = true)
	public List<MessageView> history(String userId, String conversationId, Instant before, int limit) {
		Conversation conversation = requireConversation(conversationId);
		connections.requireMember(conversation.getConnectionId(), userId);
		Instant cursor = before == null ? clock.instant().plusSeconds(1) : before;
		return messages
			.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(conversationId, cursor,
					PageRequest.of(0, Math.clamp(limit, 1, 50)))
			.stream()
			.map(m -> MessageView.of(m, userId))
			.toList();
	}

	/**
	 * Investment Balance (blueprint §20.1): qualitative only, never a number. Only the less-active side is
	 * nudged; the more-active side sees a neutral line so nobody is shamed.
	 */
	@Transactional(readOnly = true)
	public BalanceView balance(String userId, String conversationId) {
		Conversation conversation = requireConversation(conversationId);
		connections.requireMember(conversation.getConnectionId(), userId);
		Instant since = clock.instant().minus(BALANCE_WINDOW);
		long total = messages.countByConversationIdAndCreatedAtAfter(conversationId, since);
		long mine = messages.countByConversationIdAndSenderIdAndCreatedAtAfter(conversationId, userId, since);
		if (total < 6) {
			return new BalanceView(BalanceView.State.EARLY, "Just getting started.");
		}
		double share = (double) mine / total;
		if (share < 0.3) {
			return new BalanceView(BalanceView.State.NUDGE,
					"They've been sharing a lot lately. Maybe ask them something back?");
		}
		if (share > 0.7) {
			return new BalanceView(BalanceView.State.FLOWING, "This conversation is active.");
		}
		return new BalanceView(BalanceView.State.BALANCED, "You've both been equally active this week.");
	}

	/**
	 * How many of the user's conversations "went somewhere" in [from, to): both people wrote, and at least
	 * {@code minMessages} messages were exchanged. A count for the owner's private ledger only.
	 */
	@Transactional(readOnly = true)
	public int meaningfulConversations(String userId, Instant from, Instant to, int minMessages) {
		List<String> connectionIds = connections.all(userId).stream().map(Connection::getId).toList();
		if (connectionIds.isEmpty()) {
			return 0;
		}
		List<String> conversationIds = conversations.findByConnectionIdIn(connectionIds)
			.stream()
			.map(Conversation::getId)
			.toList();
		if (conversationIds.isEmpty()) {
			return 0;
		}
		return messages.findActiveConversations(conversationIds, from, to, minMessages).size();
	}

	/**
	 * Days since {@code since} on which both people wrote (in {@code zone}), and how many days have passed
	 * since the last message. Inputs for Connection Warmth, never shown as numbers.
	 */
	@Transactional(readOnly = true)
	public Rhythm rhythm(String conversationId, Instant since, ZoneId zone) {
		Map<LocalDate, Set<String>> writersByDay = new HashMap<>();
		Instant last = null;
		for (Object[] row : messages.findSendersSince(conversationId, since, PageRequest.of(0, 2000))) {
			Instant at = (Instant) row[1];
			writersByDay.computeIfAbsent(at.atZone(zone).toLocalDate(), d -> new HashSet<>()).add((String) row[0]);
			last = last == null || at.isAfter(last) ? at : last;
		}
		int mutualDays = (int) writersByDay.values().stream().filter(w -> w.size() >= 2).count();
		return new Rhythm(mutualDays, last);
	}

	/** Everyone who had a two-way conversation in [from, to): an input to Weekly Meaningful Actives. */
	@Transactional(readOnly = true)
	public Set<String> twoWayWriters(Instant from, Instant to) {
		return new HashSet<>(messages.findTwoWayWriters(from, to));
	}

	/** {@code lastMessageAt} is null when nothing was written in the window. */
	public record Rhythm(int mutualDays, Instant lastMessageAt) {
	}

	@Transactional(readOnly = true)
	public List<Message> sentBy(String userId) {
		return messages.findBySenderIdOrderByCreatedAtAsc(userId);
	}

	/** Erasure: removes the conversations (both sides' messages) attached to the given connections. */
	@Transactional
	public void deleteForConnections(Collection<String> connectionIds) {
		if (connectionIds.isEmpty()) {
			return;
		}
		List<Conversation> found = conversations.findByConnectionIdIn(connectionIds);
		e2ee.deleteForConversations(found.stream().map(Conversation::getId).toList());
		messages.deleteByConversationIds(found.stream().map(Conversation::getId).toList());
		conversations.deleteAll(found);
	}

	private Conversation requireConversation(String conversationId) {
		return conversations.findById(conversationId).orElseThrow(() -> ApiException.notFound("Conversation"));
	}

	/** A new message in one of the recipient's conversations. */
	public record RealtimeMessage(String conversationId, MessageView message) {
	}

	/**
	 * {@code pacingHint} is a private note for the sender only (Pacing Guardian), never part of history.
	 * {@code concern} is shown to the recipient only, on a message sent past the Empathy Mirror.
	 */
	public record MessageView(String id, boolean mine, String body, Instant sentAt, String pacingHint,
			Concern concern, boolean encrypted) {

		/** Encrypted messages have no body here: the devices hold it. */
		static MessageView of(Message message, String viewerId) {
			boolean mine = message.getSenderId().equals(viewerId);
			return new MessageView(message.getId(), mine, message.isEncrypted() ? null : message.getBody(),
					message.getCreatedAt(), null, EmpathyMirror.concernFor(message.getToneFlag(), mine),
					message.isEncrypted());
		}

		MessageView withHint(String hint) {
			return new MessageView(id, mine, body, sentAt, hint, concern, encrypted);
		}
	}

	public record BalanceView(State state, String message) {

		public enum State {
			EARLY, BALANCED, FLOWING, NUDGE
		}
	}
}
