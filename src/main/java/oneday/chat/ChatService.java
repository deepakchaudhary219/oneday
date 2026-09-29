package oneday.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import oneday.common.ApiException;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.identity.UserGuard;
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

	public ChatService(ConversationRepository conversations, MessageRepository messages,
			ConnectionService connections, UserGuard guard, BlockChecker blocks, Clock clock) {
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
	public MessageView send(String userId, String conversationId, String body) {
		guard.requireContactAllowed(userId);
		Conversation conversation = requireConversation(conversationId);
		Connection connection = connections.requireMember(conversation.getConnectionId(), userId);
		if (!connection.isActive() || blocks.isBlockedEitherWay(userId, connection.otherThan(userId))) {
			throw ApiException.conflict("CONVERSATION_INACTIVE", "This conversation is no longer active");
		}
		Message message = messages.save(new Message(conversationId, userId, body.strip(), clock.instant()));
		return MessageView.of(message, userId);
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
		messages.deleteByConversationIds(found.stream().map(Conversation::getId).toList());
		conversations.deleteAll(found);
	}

	private Conversation requireConversation(String conversationId) {
		return conversations.findById(conversationId).orElseThrow(() -> ApiException.notFound("Conversation"));
	}

	public record MessageView(String id, boolean mine, String body, Instant sentAt) {

		static MessageView of(Message message, String viewerId) {
			return new MessageView(message.getId(), message.getSenderId().equals(viewerId), message.getBody(),
					message.getCreatedAt());
		}
	}

	public record BalanceView(State state, String message) {

		public enum State {
			EARLY, BALANCED, FLOWING, NUDGE
		}
	}
}
