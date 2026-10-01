package oneday.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.common.ApiException;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Pacing Guardian (blueprint v2 §10, docs/05 §1.9): protects the person on the receiving end of one-sided
 * pressure without shaming the sender. Counted per conversation:
 * <ul>
 * <li>after {@link #NUDGE_AT} messages in a row without a reply, the sender gets a gentle private hint;</li>
 * <li>at {@link #PAUSE_AT} unanswered messages, sending pauses until the other person replies or a day passes
 * since the last of them;</li>
 * <li>more than {@link #BURST_LIMIT} messages a minute is slowed down (flooding, scripts).</li>
 * </ul>
 * The other person is never told. Normal back-and-forth never meets any of this.
 */
@Component
public class PacingGuardian {

	static final int NUDGE_AT = 3;

	static final int PAUSE_AT = 5;

	static final int BURST_LIMIT = 10;

	static final Duration PAUSE_WINDOW = Duration.ofHours(24);

	private final MessageRepository messages;

	private final MeterRegistry metrics;

	private final Clock clock;

	PacingGuardian(MessageRepository messages, MeterRegistry metrics, Clock clock) {
		this.messages = messages;
		this.metrics = metrics;
		this.clock = clock;
	}

	/** Checks before a send; throws when the sender should wait. Returns the unanswered streak so far. */
	int beforeSend(String conversationId, String senderId) {
		Instant now = clock.instant();
		if (messages.countByConversationIdAndSenderIdAndCreatedAtAfter(conversationId, senderId,
				now.minus(Duration.ofMinutes(1))) >= BURST_LIMIT) {
			metrics.counter("oneday.pacing", "action", "slow").increment();
			throw ApiException.tooManyRequests("PACING_SLOW_DOWN", "Slow down a little. Messages land better one at a time.");
		}
		int streak = unansweredStreak(conversationId, senderId, now);
		if (streak >= PAUSE_AT) {
			metrics.counter("oneday.pacing", "action", "pause").increment();
			throw ApiException.tooManyRequests("PACING_PAUSE",
					"Give them a moment to reply. You can write again once they do, or tomorrow.");
		}
		return streak;
	}

	/** The private hint after a send, or null. */
	String hintAfterSend(int streakBefore) {
		if (streakBefore + 1 >= NUDGE_AT) {
			metrics.counter("oneday.pacing", "action", "nudge").increment();
			return "They may be busy. Conversations flow best both ways, so maybe wait for a reply.";
		}
		return null;
	}

	/** Messages the sender wrote since the other person last wrote, counting only the last day. */
	private int unansweredStreak(String conversationId, String senderId, Instant now) {
		List<Message> recent = messages.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(conversationId,
				now.plusSeconds(1), PageRequest.of(0, PAUSE_AT));
		int streak = 0;
		for (Message m : recent) {
			if (!m.getSenderId().equals(senderId) || m.getCreatedAt().isBefore(now.minus(PAUSE_WINDOW))) {
				break;
			}
			streak++;
		}
		return streak;
	}
}
