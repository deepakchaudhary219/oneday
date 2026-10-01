package oneday.connections;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

import oneday.chat.ChatService;
import oneday.chat.ChatService.Rhythm;
import oneday.profile.Profile;

import org.springframework.stereotype.Component;

/**
 * Connection Warmth (docs/05-engagement-psychology.md §3.4): a positive replacement for streaks.
 *
 * <p>
 * Streaks hold people through loss aversion: miss a day and lose everything, so people message out of fear.
 * Warmth grows with <em>mutual</em> rhythm (days on which both people wrote, over two weeks), is shared by
 * both people, never counts down, never threatens, and is never a number. When a connection goes quiet,
 * the app offers a conversation starter drawn from what the two people genuinely share, an invitation rather
 * than a guilt trip.
 */
@Component
public class ConnectionWarmth {

	static final Duration WINDOW = Duration.ofDays(14);

	private final ChatService chat;

	private final Clock clock;

	public ConnectionWarmth(ChatService chat, Clock clock) {
		this.chat = chat;
		this.clock = clock;
	}

	public enum Level {
		/** Just connected. */
		NEW,
		/** Nothing mutual lately. Shown gently, with a starter. */
		QUIET,
		KINDLING,
		WARM,
		GLOWING
	}

	public Warmth of(Connection connection, String conversationId, Profile me, Profile them) {
		Instant now = clock.instant();
		Rhythm rhythm = conversationId == null ? new Rhythm(0, null)
				: chat.rhythm(conversationId, now.minus(WINDOW), ZoneId.of(me.getTimeZone()));
		boolean fresh = connection.getCreatedAt().isAfter(now.minus(Duration.ofDays(2)));
		Level level;
		if (rhythm.mutualDays() >= 7) {
			level = Level.GLOWING;
		}
		else if (rhythm.mutualDays() >= 3) {
			level = Level.WARM;
		}
		else if (rhythm.mutualDays() >= 1) {
			level = Level.KINDLING;
		}
		else {
			level = fresh ? Level.NEW : Level.QUIET;
		}
		String line = switch (level) {
			case NEW -> "You just connected. Say hi while it's fresh ✨";
			case QUIET -> "Quiet lately, and that's okay. Pick it up whenever you like.";
			case KINDLING -> "Something's kindling here 🔥";
			case WARM -> "You two have a nice rhythm going.";
			case GLOWING -> "This one's glowing ✨";
		};
		boolean quietForAWhile = rhythm.lastMessageAt() == null
				|| rhythm.lastMessageAt().isBefore(now.minus(Duration.ofDays(4)));
		String starter = level == Level.NEW || (level == Level.QUIET && quietForAWhile) ? starter(me, them) : null;
		return new Warmth(level, line, starter);
	}

	/** A conversation starter from what the two genuinely share (activities, roots, languages, values). */
	static String starter(Profile me, Profile them) {
		Set<String> sharedActivities = new LinkedHashSet<>(me.getActivities());
		sharedActivities.retainAll(them.getActivities());
		if (!sharedActivities.isEmpty()) {
			return "You both love " + sharedActivities.iterator().next() + ". Ask about their favourite spot for it?";
		}
		if (me.getHomeRegion() != null && me.getHomeRegion().equals(them.getHomeRegion())) {
			return "You're both from the same place. What do they miss most about home?";
		}
		Set<String> sharedValues = new LinkedHashSet<>(me.getValues());
		sharedValues.retainAll(them.getValues());
		if (!sharedValues.isEmpty()) {
			return "You both put " + sharedValues.iterator().next().toLowerCase().replace('_', ' ')
					+ " first. Ask what that looks like in their week?";
		}
		if (!them.getActivities().isEmpty()) {
			return "They're into " + them.getActivities().iterator().next() + ". Ask how they got started?";
		}
		return "Ask about the best thing that happened to them this week.";
	}

	/** Never a number, never a countdown. */
	public record Warmth(Level level, String line, String starter) {
	}
}
