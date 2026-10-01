package oneday.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The published language between modules: facts that already happened, named in the past tense. Every
 * event type lives here so the catalogue is reviewable in one place, and so a serialized event can always be
 * mapped back to its type. Adding a field is backwards compatible (older rows deserialize with a null);
 * anything else needs a new {@link #SCHEMA_VERSION}.
 *
 * <p>
 * Events carry internal ids only. They never leave the server as they are: consumers turn them into
 * projections or side effects that apply the usual visibility rules.
 */
public sealed interface DomainEvent {

	int SCHEMA_VERSION = 1;

	/** The entity the event is about; also the ordering key once a broker partitions the stream. */
	String aggregateId();

	/** The accounts the event mentions, so their events can be erased with them. */
	List<String> userIds();

	default String type() {
		return getClass().getSimpleName();
	}

	/** A moment went live. */
	record MomentPublished(String momentId, String ownerId, String kind, String shareScope) implements DomainEvent {

		public String aggregateId() {
			return momentId;
		}

		public List<String> userIds() {
			return List.of(ownerId);
		}
	}

	/** Someone answered another person's public story with their own (a Story Relay link). */
	record RelayJoined(String momentId, String relayRootId, String joinerId, String answeredOwnerId)
			implements DomainEvent {

		public String aggregateId() {
			return relayRootId;
		}

		public List<String> userIds() {
			return List.of(joinerId, answeredOwnerId);
		}
	}

	/**
	 * A push notification to deliver. Pushes go through the outbox so a slow or failing push provider never
	 * holds a request's database transaction open, and a failed push is retried.
	 */
	record PushRequested(String userId, String title, String body, Map<String, String> data) implements DomainEvent {

		public String aggregateId() {
			return userId;
		}

		public List<String> userIds() {
			return List.of(userId);
		}
	}

	/** A Signal was sent (Layer 1). */
	record SignalSent(String signalId, String senderId, String recipientId) implements DomainEvent {

		public String aggregateId() {
			return signalId;
		}

		public List<String> userIds() {
			return List.of(senderId, recipientId);
		}
	}

	/** A Mutual Reveal created (or revived) a Connection (Layer 2). */
	record MutualRevealed(String connectionId, String userA, String userB) implements DomainEvent {

		public String aggregateId() {
			return connectionId;
		}

		public List<String> userIds() {
			return List.of(userA, userB);
		}
	}

	/** Both people in a Connection sparked. Emitted once per transition to mutual. */
	record MutualSparked(String connectionId, String userA, String userB) implements DomainEvent {

		public String aggregateId() {
			return connectionId;
		}

		public List<String> userIds() {
			return List.of(userA, userB);
		}
	}

	/** Both people confirmed "we're seeing each other" (blueprint v2 §7.4). */
	record CoupleFormed(String connectionId, String userA, String userB) implements DomainEvent {

		public String aggregateId() {
			return connectionId;
		}

		public List<String> userIds() {
			return List.of(userA, userB);
		}
	}

	record UserBlocked(String blockerId, String blockedId) implements DomainEvent {

		public String aggregateId() {
			return blockerId;
		}

		public List<String> userIds() {
			return List.of(blockerId, blockedId);
		}
	}

	/** Both people agreed to a Date Mode plan. */
	record DateConfirmed(String dateId, String proposerId, String partnerId, Instant startsAt) implements DomainEvent {

		public String aggregateId() {
			return dateId;
		}

		public List<String> userIds() {
			return List.of(proposerId, partnerId);
		}
	}

	/** A confirmed plan reached its end: the two people actually met up. */
	record DateCompleted(String dateId, String userA, String userB) implements DomainEvent {

		public String aggregateId() {
			return dateId;
		}

		public List<String> userIds() {
			return List.of(userA, userB);
		}
	}

	/**
	 * Someone on a date asked for help, or missed a check-in. Consumers alert the person's trusted contact;
	 * the other person on the date is never told.
	 */
	record DateSafetyEscalated(String dateId, String userId, String reason) implements DomainEvent {

		public String aggregateId() {
			return dateId;
		}

		public List<String> userIds() {
			return List.of(userId);
		}
	}
}
