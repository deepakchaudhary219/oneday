package oneday.ama;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import oneday.common.ApiException;
import oneday.empathy.EmpathyMirror;
import oneday.empathy.EmpathyMirror.Concern;
import oneday.empathy.Tone;
import oneday.figures.PublicFigureService;
import oneday.figures.PublicFigureService.FigureView;
import oneday.identity.UserGuard;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;
import oneday.staff.StaffDirectory;
import oneday.staff.StaffRole;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AMA Corridors (v3; docs/07-v3-features.md): a verified Public Figure answers questions for 15 to 120 minutes,
 * either for everyone or for one Roots corridor, the people whose home region matches (say IN-KL: Keralites
 * wherever they now live).
 *
 * <ul>
 * <li>Questions open a day before the start: up to three per person, 280 characters, through the Empathy
 * Mirror, optionally anonymous ("Someone from IN-KL").</li>
 * <li>Upvotes order the questions; counts are shown to the host only, never publicly.</li>
 * <li>The host answers during the window and can hide questions; moderators can too. Questions are reportable
 * by {@code amaQuestionId}.</li>
 * <li>Everything is deleted 30 days after the AMA ends.</li>
 * </ul>
 */
@Service
public class AmaService {

	private static final Pattern REGION = Pattern.compile("[A-Z]{2}-[A-Z0-9]{1,3}");

	static final Duration QUESTIONS_OPEN_BEFORE = Duration.ofDays(1);

	static final Duration LISTED_AHEAD = Duration.ofDays(30);

	static final Duration RETENTION = Duration.ofDays(30);

	static final int MAX_QUESTIONS_EACH = 3;

	private final AmaRepository amas;

	private final AmaQuestionRepository questions;

	private final AmaVoteRepository votes;

	private final PublicFigureService figures;

	private final ProfileService profiles;

	private final EmpathyMirror empathy;

	private final StaffDirectory staff;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final Clock clock;

	public AmaService(AmaRepository amas, AmaQuestionRepository questions, AmaVoteRepository votes,
			PublicFigureService figures, ProfileService profiles, EmpathyMirror empathy, StaffDirectory staff,
			BlockChecker blocks, UserGuard guard, Clock clock) {
		this.amas = amas;
		this.questions = questions;
		this.votes = votes;
		this.figures = figures;
		this.profiles = profiles;
		this.empathy = empathy;
		this.staff = staff;
		this.blocks = blocks;
		this.guard = guard;
		this.clock = clock;
	}

	@Transactional
	public AmaView schedule(String hostId, String title, String corridorRegion, Instant startsAt, int minutes) {
		guard.requireContactAllowed(hostId);
		FigureView host = figures.approved(hostId)
			.orElseThrow(() -> ApiException.forbidden("FIGURES_ONLY", "AMAs are hosted by verified Public Figures"));
		String name = title == null ? "" : title.strip();
		if (name.isEmpty() || name.length() > 100) {
			throw ApiException.badRequest("TITLE_REQUIRED", "Give the AMA a title (up to 100 characters)");
		}
		String region = null;
		if (corridorRegion != null && !corridorRegion.isBlank()) {
			region = corridorRegion.strip().toUpperCase(Locale.ROOT);
			if (!REGION.matcher(region).matches()) {
				throw ApiException.badRequest("INVALID_REGION", "A corridor is a home region such as IN-KL");
			}
		}
		Instant now = clock.instant();
		if (startsAt == null || startsAt.isBefore(now) || startsAt.isAfter(now.plus(LISTED_AHEAD))) {
			throw ApiException.badRequest("INVALID_START", "Start within the next 30 days");
		}
		if (minutes < 15 || minutes > 120) {
			throw ApiException.badRequest("INVALID_DURATION", "An AMA lasts 15 to 120 minutes");
		}
		Ama ama = amas.save(new Ama(hostId, name, region, startsAt, startsAt.plus(Duration.ofMinutes(minutes)), now));
		return view(ama, hostId, host);
	}

	/** Upcoming (next 30 days) and live AMAs the viewer may join. */
	@Transactional(readOnly = true)
	public List<AmaView> list(String viewerId) {
		guard.requireActive(viewerId);
		Instant now = clock.instant();
		String home = homeRegion(viewerId);
		Set<String> blocked = blocks.blockedEitherWay(viewerId);
		return amas.findCurrent(now, now.plus(LISTED_AHEAD))
			.stream()
			.filter(a -> !blocked.contains(a.getHostId()))
			.filter(a -> a.getCorridorRegion() == null || a.getCorridorRegion().equals(home)
					|| a.getHostId().equals(viewerId))
			.map(a -> figures.approved(a.getHostId()).map(host -> view(a, viewerId, host)))
			.flatMap(Optional::stream)
			.toList();
	}

	@Transactional(readOnly = true)
	public AmaDetail get(String viewerId, String amaId) {
		Ama ama = requireVisible(viewerId, amaId);
		FigureView host = figures.approved(ama.getHostId()).orElseThrow(() -> ApiException.notFound("AMA"));
		boolean isHost = ama.getHostId().equals(viewerId);
		Set<String> blocked = blocks.blockedEitherWay(viewerId);
		List<AmaQuestion> all = questions.findByAmaId(amaId)
			.stream()
			.filter(q -> !q.isHidden() || isHost)
			.filter(q -> !blocked.contains(q.getAskerId()))
			.toList();
		List<String> ids = all.stream().map(AmaQuestion::getId).toList();
		Map<String, Long> counts = new HashMap<>();
		Set<String> mine = new HashSet<>();
		if (!ids.isEmpty()) {
			votes.countFor(ids).forEach(row -> counts.put((String) row[0], (Long) row[1]));
			mine.addAll(votes.votedBy(viewerId, ids));
		}
		List<QuestionView> ordered = all.stream()
			.sorted(Comparator.comparing((AmaQuestion q) -> q.getAnswer() == null)
				.thenComparing(q -> -counts.getOrDefault(q.getId(), 0L))
				.thenComparing(AmaQuestion::getCreatedAt))
			.map(q -> questionView(q, viewerId, isHost ? counts.getOrDefault(q.getId(), 0L) : null,
					mine.contains(q.getId())))
			.toList();
		return new AmaDetail(view(ama, viewerId, host), ordered);
	}

	@Transactional
	public QuestionView ask(String userId, String amaId, String body, boolean anonymous, boolean sendAnyway) {
		guard.requireContactAllowed(userId);
		Ama ama = requireVisible(userId, amaId);
		Instant now = clock.instant();
		if (ama.getHostId().equals(userId)) {
			throw ApiException.unprocessable("HOST_CANNOT_ASK", "Hosts answer; they don't ask");
		}
		Ama.State state = ama.state(now);
		if (state == Ama.State.ENDED || state == Ama.State.CANCELLED
				|| now.isBefore(ama.getStartsAt().minus(QUESTIONS_OPEN_BEFORE))) {
			throw ApiException.conflict("QUESTIONS_CLOSED", "Questions open a day before the AMA and close when it ends");
		}
		String text = body == null ? "" : body.strip();
		if (text.isEmpty() || text.length() > 280) {
			throw ApiException.badRequest("QUESTION_REQUIRED", "Ask something (up to 280 characters)");
		}
		if (questions.countByAmaIdAndAskerId(amaId, userId) >= MAX_QUESTIONS_EACH) {
			throw ApiException.conflict("QUESTION_LIMIT", "Three questions each, so everyone gets a turn");
		}
		Optional<Tone> tone = empathy.reflect("ama", text, sendAnyway);
		AmaQuestion question = questions.save(new AmaQuestion(amaId, userId, text, anonymous,
				tone.map(Enum::name).orElse(null), now));
		return questionView(question, userId, null, false);
	}

	@Transactional
	public void vote(String userId, String amaId, String questionId, boolean up) {
		guard.requireActive(userId);
		requireVisible(userId, amaId);
		AmaQuestion question = questions.findById(questionId)
			.filter(q -> q.getAmaId().equals(amaId) && !q.isHidden())
			.orElseThrow(() -> ApiException.notFound("Question"));
		AmaVote.Key key = new AmaVote.Key(question.getId(), userId);
		if (up && !votes.existsById(key)) {
			votes.save(new AmaVote(question.getId(), userId));
		}
		else if (!up) {
			votes.deleteById(key);
		}
	}

	@Transactional
	public QuestionView answer(String hostId, String amaId, String questionId, String text) {
		Ama ama = amas.findById(amaId).filter(a -> a.getHostId().equals(hostId))
			.orElseThrow(() -> ApiException.notFound("AMA"));
		if (ama.state(clock.instant()) != Ama.State.LIVE) {
			throw ApiException.conflict("NOT_LIVE", "Answers go out while the AMA is live");
		}
		String answer = text == null ? "" : text.strip();
		if (answer.isEmpty() || answer.length() > 2000) {
			throw ApiException.badRequest("ANSWER_REQUIRED", "Answers are 1 to 2000 characters");
		}
		AmaQuestion question = questions.findById(questionId).filter(q -> q.getAmaId().equals(amaId))
			.orElseThrow(() -> ApiException.notFound("Question"));
		question.answer(answer, clock.instant());
		return questionView(question, hostId, null, false);
	}

	/** The host, or a moderator, hides a question (it stays visible to the host only). */
	@Transactional
	public void hide(String userId, String amaId, String questionId) {
		Ama ama = amas.findById(amaId).orElseThrow(() -> ApiException.notFound("AMA"));
		if (!ama.getHostId().equals(userId)) {
			staff.require(userId, StaffRole.MODERATOR);
		}
		questions.findById(questionId).filter(q -> q.getAmaId().equals(amaId))
			.orElseThrow(() -> ApiException.notFound("Question"))
			.hide();
	}

	@Transactional
	public void cancel(String hostId, String amaId) {
		Ama ama = amas.findById(amaId).filter(a -> a.getHostId().equals(hostId))
			.orElseThrow(() -> ApiException.notFound("AMA"));
		if (ama.state(clock.instant()) != Ama.State.UPCOMING) {
			throw ApiException.conflict("ALREADY_STARTED", "Only an AMA that hasn't started can be cancelled");
		}
		ama.cancel();
	}

	/** The asker, for blocking or reporting a question; anyone who can see the AMA can name it. */
	@Transactional(readOnly = true)
	public Optional<String> askerOf(String viewerId, String questionId) {
		return questions.findById(questionId)
			.filter(q -> {
				try {
					requireVisible(viewerId, q.getAmaId());
					return true;
				}
				catch (ApiException ex) {
					return false;
				}
			})
			.map(AmaQuestion::getAskerId);
	}

	@Scheduled(fixedDelayString = "${oneday.ama.sweep-interval:PT1H}", initialDelayString = "PT9M")
	@Transactional
	public int purgeOld() {
		List<String> old = amas.findByEndsAtBefore(clock.instant().minus(RETENTION)).stream().map(Ama::getId).toList();
		delete(old);
		return old.size();
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> export(String userId) {
		return questions.findByAskerId(userId)
			.stream()
			.map(q -> Map.<String, Object>of("amaId", q.getAmaId(), "question", q.getBody(), "askedAt",
					q.getCreatedAt()))
			.toList();
	}

	@Transactional
	public void forget(String userId) {
		delete(amas.findByHostId(userId).stream().map(Ama::getId).toList());
		List<String> asked = questions.findByAskerId(userId).stream().map(AmaQuestion::getId).toList();
		if (!asked.isEmpty()) {
			votes.deleteForQuestions(asked);
		}
		questions.deleteByAsker(userId);
		votes.deleteByVoter(userId);
	}

	private void delete(List<String> amaIds) {
		if (amaIds.isEmpty()) {
			return;
		}
		List<String> questionIds = amaIds.stream()
			.flatMap(id -> questions.findByAmaId(id).stream())
			.map(AmaQuestion::getId)
			.toList();
		if (!questionIds.isEmpty()) {
			votes.deleteForQuestions(questionIds);
		}
		questions.deleteByAmas(amaIds);
		amas.deleteAllById(amaIds);
	}

	private Ama requireVisible(String viewerId, String amaId) {
		Ama ama = amas.findById(amaId).orElseThrow(() -> ApiException.notFound("AMA"));
		boolean host = ama.getHostId().equals(viewerId);
		if (!host && (blocks.isBlockedEitherWay(viewerId, ama.getHostId())
				|| (ama.getCorridorRegion() != null && !ama.getCorridorRegion().equals(homeRegion(viewerId))))) {
			throw ApiException.notFound("AMA");
		}
		return ama;
	}

	private String homeRegion(String userId) {
		return profiles.find(userId).map(Profile::getHomeRegion).orElse(null);
	}

	private AmaView view(Ama a, String viewerId, FigureView host) {
		Instant now = clock.instant();
		Ama.State state = a.state(now);
		boolean canAsk = !a.getHostId().equals(viewerId) && (state == Ama.State.LIVE
				|| (state == Ama.State.UPCOMING && !now.isBefore(a.getStartsAt().minus(QUESTIONS_OPEN_BEFORE))));
		return new AmaView(a.getId(), a.getTitle(), host.handle(), host.publicName(), a.getCorridorRegion(),
				a.getStartsAt(), a.getEndsAt(), state.name(), canAsk, a.getHostId().equals(viewerId));
	}

	private QuestionView questionView(AmaQuestion q, String viewerId, Long votesForHost, boolean youVoted) {
		boolean mine = q.getAskerId().equals(viewerId);
		String asker = q.isAnonymous() ? "Someone" + regionSuffix(q.getAskerId())
				: profiles.find(q.getAskerId()).map(Profile::firstName).orElse("Someone");
		return new QuestionView(q.getId(), asker, mine, q.getBody(), q.getAnswer(), q.getAnsweredAt(), youVoted,
				votesForHost, q.isHidden(), EmpathyMirror.concernFor(q.getToneFlag(), mine));
	}

	private String regionSuffix(String userId) {
		String region = homeRegion(userId);
		return region == null ? "" : " from " + region;
	}

	/** {@code youHost}: the viewer is the host. */
	public record AmaView(String id, String title, String hostHandle, String hostName, String corridorRegion,
			Instant startsAt, Instant endsAt, String state, boolean canAsk, boolean youHost) {
	}

	public record AmaDetail(AmaView ama, List<QuestionView> questions) {
	}

	/** {@code votes} is filled for the host only; {@code concern} is the Empathy Mirror's prompt for readers. */
	public record QuestionView(String id, String askedBy, boolean mine, String question, String answer,
			Instant answeredAt, boolean youVoted, Long votes, boolean hidden, Concern concern) {
	}
}
