package oneday.ledger;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import oneday.chat.ChatService;
import oneday.identity.UserGuard;
import oneday.ledger.LedgerEntry.Kind;
import oneday.moments.Moment;
import oneday.moments.MomentService;
import oneday.profile.ProfileService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Weekly Recap (docs/05-engagement-psychology.md §6): last week as a short, warm, private story. It is
 * built on the peak-end rule: one highlight (the best real thing that happened), a few facts, and a kind
 * ending. It is never shown to anyone else and never compared with anyone.
 */
@Service
public class WeeklyRecap {

	static final int MEANINGFUL_CONVERSATION_MESSAGES = 6;

	private final LedgerRepository ledger;

	private final MomentService moments;

	private final ChatService chat;

	private final ProfileService profiles;

	private final UserGuard guard;

	private final Clock clock;

	public WeeklyRecap(LedgerRepository ledger, MomentService moments, ChatService chat, ProfileService profiles,
			UserGuard guard, Clock clock) {
		this.ledger = ledger;
		this.moments = moments;
		this.chat = chat;
		this.profiles = profiles;
		this.guard = guard;
		this.clock = clock;
	}

	/** The last complete week (Monday to Sunday) in the person's own time zone. */
	@Transactional(readOnly = true)
	public RecapView lastWeek(String userId) {
		guard.requireExisting(userId);
		ZoneId zone = ZoneId.of(profiles.require(userId).getTimeZone());
		LocalDate thisMonday = LocalDate.now(clock.withZone(zone)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate start = thisMonday.minusWeeks(1);
		Instant from = start.atStartOfDay(zone).toInstant();
		Instant to = thisMonday.atStartOfDay(zone).toInstant();

		Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
		for (Kind kind : Kind.values()) {
			counts.put(kind, 0);
		}
		ledger.findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(userId, from, to)
			.forEach(e -> counts.merge(e.getKind(), 1, Integer::sum));
		int conversations = chat.meaningfulConversations(userId, from, to, MEANINGFUL_CONVERSATION_MESSAGES);
		List<Moment> posted = moments.createdBy(userId, from, to);
		int prompts = (int) posted.stream().filter(m -> m.getPromptKey() != null).count();
		int relaysJoined = (int) posted.stream().filter(m -> m.getRelayRootId() != null).count();
		long relayAnswers = moments.relayAnswersEver(posted.stream().map(Moment::getId).toList());

		String highlight = highlight(counts, conversations, relayAnswers, prompts);
		List<String> lines = new ArrayList<>();
		add(lines, counts.get(Kind.NEW_CONNECTION), "new connection", "new connections");
		add(lines, conversations, "conversation that went somewhere", "conversations that went somewhere");
		add(lines, counts.get(Kind.DATE_COMPLETED), "plan you actually went to", "plans you actually went to");
		add(lines, (int) Math.min(relayAnswers, 99), "person answered your stories", "people answered your stories");
		add(lines, relaysJoined, "relay you joined", "relays you joined");
		add(lines, prompts, "prompt you answered", "prompts you answered");
		add(lines, posted.size(), "moment shared", "moments shared");
		boolean empty = highlight == null && lines.isEmpty();
		String ending = empty ? "A quiet week, and that's okay. The city will still be here when you are."
				: "That's a real week. Have a good one ✨";
		return new RecapView(start, thisMonday.minusDays(1), highlight, lines, ending, !empty);
	}

	/** The peak: the single best real thing that happened, in order of how much it matters. */
	private static String highlight(Map<Kind, Integer> counts, int conversations, long relayAnswers, int prompts) {
		if (counts.get(Kind.COUPLE_FORMED) > 0) {
			return "You switched on Couple Mode. That's the app doing its job 💛";
		}
		if (counts.get(Kind.DATE_COMPLETED) > 0) {
			return "You met someone in real life this week.";
		}
		if (counts.get(Kind.MUTUAL_SPARK) > 0) {
			return "You and someone both sparked ✨";
		}
		if (counts.get(Kind.ROOTS_CONNECTION) > 0) {
			return "You found someone from home in your city.";
		}
		if (counts.get(Kind.NEW_CONNECTION) > 0) {
			return "You made a new connection.";
		}
		if (conversations > 0) {
			return "A conversation of yours really went somewhere.";
		}
		if (relayAnswers > 0) {
			return "Your story started a relay.";
		}
		if (prompts > 0) {
			return "You put your corner of the city on the map.";
		}
		return null;
	}

	private static void add(List<String> lines, int n, String one, String many) {
		if (n > 0) {
			lines.add(n + " " + (n == 1 ? one : many));
		}
	}

	/** {@code ready} is true when there is something to show. */
	public record RecapView(LocalDate weekStart, LocalDate weekEnd, String highlight, List<String> lines,
			String ending, boolean ready) {
	}
}
