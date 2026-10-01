package oneday.wellbeing;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import oneday.chat.ChatService;
import oneday.common.ApiException;
import oneday.identity.UserGuard;
import oneday.staff.StaffDirectory;
import oneday.staff.StaffRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Honest measurement (docs/05-engagement-psychology.md §5).
 *
 * <ul>
 * <li><b>Weekly Meaningful Actives</b>, the north star: people who had at least one two-way interaction in an
 * ISO week (UTC): a reveal, a mutual spark, Couple Mode, a relay answer, a date, or a conversation in which
 * both people wrote.</li>
 * <li><b>"Was your time on OneDay well spent?"</b>, the guardrail: asked rarely (about 1 in 50 person-days,
 * never more than once per {@code minGap}), never blocking, one tap. A product that raises time spent while
 * this falls is drifting into compulsion.</li>
 * </ul>
 */
@Service
public class WellbeingService implements MeterBinder {

	static final String QUESTION = "Was your time on OneDay well spent today?";

	private final WeeklyActiveRepository weekly;

	private final WellbeingAnswerRepository answers;

	private final ChatService chat;

	private final UserGuard guard;

	private final StaffDirectory staff;

	private final WellbeingProperties settings;

	private final Clock clock;

	public WellbeingService(WeeklyActiveRepository weekly, WellbeingAnswerRepository answers, ChatService chat,
			UserGuard guard, StaffDirectory staff, WellbeingProperties settings, Clock clock) {
		this.weekly = weekly;
		this.answers = answers;
		this.chat = chat;
		this.guard = guard;
		this.staff = staff;
		this.settings = settings;
		this.clock = clock;
	}

	// ---- the question ----------------------------------------------------------------------------

	/** Whether the app should ask today. Deterministic per person and day, so reopening doesn't re-roll it. */
	@Transactional(readOnly = true)
	public CheckView check(String userId) {
		guard.requireActive(userId);
		Instant now = clock.instant();
		if (answers.existsByUserIdAndCreatedAtAfter(userId, now.minus(settings.minGap()))) {
			return new CheckView(false, null);
		}
		CRC32 crc = new CRC32();
		crc.update((userId + "|" + LocalDate.ofInstant(now, ZoneOffset.UTC)).getBytes(StandardCharsets.UTF_8));
		boolean ask = crc.getValue() % 100 < settings.askPercent();
		return new CheckView(ask, ask ? QUESTION : null);
	}

	@Transactional
	public AnswerView answer(String userId, boolean wellSpent) {
		guard.requireActive(userId);
		Instant now = clock.instant();
		if (answers.existsByUserIdAndCreatedAtAfter(userId, now.minus(settings.minGap()))) {
			throw ApiException.conflict("ALREADY_ANSWERED", "Thanks, you've already told us recently");
		}
		answers.save(new WellbeingAnswer(userId, wellSpent, now));
		return new AnswerView(wellSpent ? "Thanks! That's what we're here for."
				: "Thanks for telling us. You're in control: change your Local Pulse hour, narrow your radius, "
						+ "or pause discovery any time in settings.");
	}

	// ---- Weekly Meaningful Actives -------------------------------------------------------------------

	/** Records that the person had a meaningful interaction now (idempotent per week). */
	@Transactional
	public void recordMeaningful(String userId, Instant at) {
		WeeklyActive.Key key = new WeeklyActive.Key(userId, weekStart(at));
		if (!weekly.existsById(key)) {
			weekly.save(new WeeklyActive(userId, key.weekStart(), at));
		}
	}

	/** The last {@code weeks} ISO weeks, newest first. Admins only. */
	@Transactional(readOnly = true)
	public List<WeekView> engagement(String staffId, int weeks) {
		staff.require(staffId, StaffRole.ADMIN);
		LocalDate current = weekStart(clock.instant());
		List<WeekView> result = new ArrayList<>();
		for (int i = 0; i < Math.clamp(weeks, 1, 26); i++) {
			LocalDate start = current.minusWeeks(i);
			Instant from = start.atStartOfDay(ZoneOffset.UTC).toInstant();
			Instant to = start.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant();
			List<WellbeingAnswer> week = answers.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
			long yes = week.stream().filter(WellbeingAnswer::isWellSpent).count();
			Double share = week.size() >= settings.minAnswersToReport()
					? Math.round(1000.0 * yes / week.size()) / 1000.0 : null;
			result.add(new WeekView(start, meaningfulActives(start), week.size(), share));
		}
		return result;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder("oneday.wma.current_week", () -> meaningfulActives(weekStart(clock.instant())))
			.description("Weekly Meaningful Actives so far this ISO week (the north-star metric)")
			.register(registry);
	}

	int meaningfulActives(LocalDate weekStart) {
		Set<String> people = new HashSet<>(weekly.findUserIdsByWeek(weekStart));
		people.addAll(chat.twoWayWriters(weekStart.atStartOfDay(ZoneOffset.UTC).toInstant(),
				weekStart.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
		return people.size();
	}

	@Transactional
	public void forget(String userId) {
		weekly.deleteByUserId(userId);
		answers.deleteByUserId(userId);
	}

	@Transactional(readOnly = true)
	public List<WellbeingAnswer> answersBy(String userId) {
		return answers.findByUserIdOrderByCreatedAtAsc(userId);
	}

	static LocalDate weekStart(Instant at) {
		return LocalDate.ofInstant(at, ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/** {@code question} is null when the app should not ask. */
	public record CheckView(boolean ask, String question) {
	}

	public record AnswerView(String message) {
	}

	/** {@code wellSpentShare} is null until enough people answered that week. */
	public record WeekView(LocalDate weekStart, int meaningfulActives, int wellbeingAnswers, Double wellSpentShare) {
	}
}
