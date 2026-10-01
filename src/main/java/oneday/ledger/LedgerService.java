package oneday.ledger;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import oneday.chat.ChatService;
import oneday.common.ApiException;
import oneday.identity.UserGuard;
import oneday.ledger.LedgerEntry.Kind;
import oneday.profile.ProfileService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Real Value Ledger (blueprint v2 §7.3): a private monthly answer to "is this app worth my time?",
 * counted in real outcomes. It is only ever shown to its owner and never compares, ranks or scores anyone.
 */
@Service
public class LedgerService {

	/** Messages both ways in a month before a conversation counts as one that "went somewhere". */
	static final int MEANINGFUL_CONVERSATION_MESSAGES = 10;

	private final LedgerRepository ledger;

	private final ChatService chat;

	private final ProfileService profiles;

	private final UserGuard guard;

	private final Clock clock;

	public LedgerService(LedgerRepository ledger, ChatService chat, ProfileService profiles, UserGuard guard,
			Clock clock) {
		this.ledger = ledger;
		this.chat = chat;
		this.profiles = profiles;
		this.guard = guard;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public LedgerView month(String userId, String month) {
		guard.requireExisting(userId);
		ZoneId zone = ZoneId.of(profiles.require(userId).getTimeZone());
		YearMonth period;
		try {
			period = month == null || month.isBlank() ? YearMonth.now(clock.withZone(zone)) : YearMonth.parse(month);
		}
		catch (RuntimeException ex) {
			throw ApiException.badRequest("INVALID_MONTH", "Use a month like 2026-09");
		}
		Instant from = period.atDay(1).atStartOfDay(zone).toInstant();
		Instant to = period.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
		Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
		for (Kind kind : Kind.values()) {
			counts.put(kind, 0);
		}
		ledger.findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(userId, from, to)
			.forEach(e -> counts.merge(e.getKind(), 1, Integer::sum));
		int conversations = chat.meaningfulConversations(userId, from, to, MEANINGFUL_CONVERSATION_MESSAGES);
		return new LedgerView(period.toString(), counts.get(Kind.NEW_CONNECTION), counts.get(Kind.ROOTS_CONNECTION),
				conversations, counts.get(Kind.MUTUAL_SPARK), counts.get(Kind.DATE_COMPLETED),
				counts.get(Kind.COUPLE_FORMED), lines(counts, conversations));
	}

	@Transactional(readOnly = true)
	public List<LedgerEntry> allFor(String userId) {
		return ledger.findByUserIdOrderByOccurredAtAsc(userId);
	}

	@Transactional
	public void forget(String userId) {
		ledger.deleteByUserId(userId);
	}

	private static List<String> lines(Map<Kind, Integer> counts, int conversations) {
		List<String> lines = new ArrayList<>();
		int connections = counts.get(Kind.NEW_CONNECTION);
		int roots = counts.get(Kind.ROOTS_CONNECTION);
		if (connections > 0) {
			lines.add(plural(connections, "new connection", "new connections")
					+ (roots > 0 ? " (" + roots + " from your home region)" : ""));
		}
		if (conversations > 0) {
			lines.add(plural(conversations, "conversation that went somewhere", "conversations that went somewhere"));
		}
		if (counts.get(Kind.MUTUAL_SPARK) > 0) {
			lines.add(plural(counts.get(Kind.MUTUAL_SPARK), "mutual spark", "mutual sparks"));
		}
		if (counts.get(Kind.DATE_COMPLETED) > 0) {
			lines.add(plural(counts.get(Kind.DATE_COMPLETED), "plan you actually went to", "plans you actually went to"));
		}
		if (counts.get(Kind.COUPLE_FORMED) > 0) {
			lines.add("You switched on Couple Mode. That's the app doing its job 💛");
		}
		if (lines.isEmpty()) {
			lines.add("Nothing yet this month, and that's fine. Real things take time.");
		}
		return lines;
	}

	private static String plural(int n, String one, String many) {
		return n + " " + (n == 1 ? one : many);
	}

	/** Counts of real outcomes for the owner only. No rank, no percentile, no comparison. */
	public record LedgerView(String month, int newConnections, int rootsConnections, int meaningfulConversations,
			int mutualSparks, int datesCompleted, int coupleFormed, List<String> lines) {
	}
}
