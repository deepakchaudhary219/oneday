package oneday.prompts;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import oneday.common.ApiException;
import oneday.config.OneDayProperties;
import oneday.connections.ConnectionService;
import oneday.geo.GeoCell;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.moments.Moment;
import oneday.moments.MomentKind;
import oneday.moments.MomentService;
import oneday.moments.ShareScope;
import oneday.profile.ActivityTags;
import oneday.profile.Affinity;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.prompts.PromptCatalog.Prompt;
import oneday.safety.BlockChecker;
import oneday.staff.StaffAudit;
import oneday.staff.StaffDirectory;
import oneday.staff.StaffRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Today's Prompt (docs/05-engagement-psychology.md §3.2): one easy, local prompt a day that the whole city
 * answers together. It runs on <em>reciprocity</em>: other people's answers unlock once you share your own
 * public answer (give to get). Until then you see a truthful teaser (how many people near you answered, and
 * how many of them are from your home region), which opens a curiosity gap that only contributing closes.
 * Nothing is fabricated, nothing expires with a countdown, and the list is bounded.
 */
@Service
public class PromptService {

	static final int MAX_ANSWERS = 20;

	private final PromptCatalog catalog;

	private final DailyPromptRepository scheduled;

	private final FestivalSeasonRepository festivals;

	private final MomentService moments;

	private final LocationService locations;

	private final ProfileService profiles;

	private final ConnectionService connections;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final StaffDirectory staff;

	private final StaffAudit audit;

	private final OneDayProperties.Discovery discovery;

	private final Clock clock;

	public PromptService(PromptCatalog catalog, DailyPromptRepository scheduled, FestivalSeasonRepository festivals,
			MomentService moments,
			LocationService locations, ProfileService profiles, ConnectionService connections, BlockChecker blocks,
			UserGuard guard, StaffDirectory staff, StaffAudit audit, OneDayProperties properties, Clock clock) {
		this.catalog = catalog;
		this.scheduled = scheduled;
		this.festivals = festivals;
		this.moments = moments;
		this.locations = locations;
		this.profiles = profiles;
		this.connections = connections;
		this.blocks = blocks;
		this.guard = guard;
		this.staff = staff;
		this.audit = audit;
		this.discovery = properties.discovery();
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TodayView today(String userId) {
		guard.requireActive(userId);
		Prompt prompt = catalog.todayFor(userId);
		List<Moment> mine = moments.answersBy(userId, prompt.key());
		boolean answered = !mine.isEmpty();
		boolean unlocked = mine.stream().anyMatch(m -> m.getShareScope() == ShareScope.PUBLIC_DISCOVERY);
		Optional<GeoCell> here = locations.currentCell(userId);
		if (here.isEmpty()) {
			return new TodayView(prompt.key(), prompt.text(), prompt.activityHint(), prompt.roots(), prompt.festival(),
					answered, unlocked, "0", 0, List.of(), "Share your location to see how people near you answered.");
		}
		Profile me = profiles.require(userId);
		List<Answer> answers = answersNear(userId, me, here.get(), prompt.key());
		long fromHome = answers.stream().filter(Answer::fromYourHomeRegion).count();
		String count = answers.size() > 9 ? "9+" : String.valueOf(answers.size());
		String message;
		if (unlocked) {
			message = answers.isEmpty() ? "You're the first near you today. Others will see yours."
					: "Here's how people near you answered.";
		}
		else if (answers.isEmpty()) {
			message = "Be the first near you to answer.";
		}
		else {
			message = count + (answers.size() == 1 ? " person" : " people") + " near you answered"
					+ (fromHome > 0 ? " (" + fromHome + " from your home region)" : "")
					+ ". Share yours publicly to see theirs.";
		}
		return new TodayView(prompt.key(), prompt.text(), prompt.activityHint(), prompt.roots(), prompt.festival(),
				answered, unlocked, count, (int) Math.min(fromHome, 9), unlocked ? answers.stream().limit(MAX_ANSWERS).toList() : List.of(),
				message);
	}

	/**
	 * Live public answers near the viewer by people they may see (verified, active, still sharing location,
	 * not blocked, not in Couple Mode), best shared context first.
	 */
	private List<Answer> answersNear(String userId, Profile me, GeoCell here, String key) {
		Set<String> excluded = new HashSet<>(blocks.blockedEitherWay(userId));
		excluded.add(userId);
		Map<String, Moment> latestByOwner = new LinkedHashMap<>();
		for (Moment m : moments.livePublicNear(here, discovery.cityRadiusKm())) {
			if (key.equals(m.getPromptKey()) && !excluded.contains(m.getOwnerId())) {
				latestByOwner.putIfAbsent(m.getOwnerId(), m);
			}
		}
		Set<String> visible = new HashSet<>(guard.reachableAmong(latestByOwner.keySet()));
		visible.retainAll(locations.currentCells(visible).keySet());
		visible.removeAll(connections.inCouple(visible));
		return latestByOwner.values()
			.stream()
			.filter(m -> visible.contains(m.getOwnerId()))
			.map(m -> {
				Profile owner = profiles.require(m.getOwnerId());
				Affinity affinity = Affinity.between(me, owner);
				return new Ranked(new Answer(m.getId(), owner.firstName(), m.getKind(), m.getActivityTag(),
						moments.previewUrl(m), m.isCapturedLive(), affinity.rootsMatch(),
						affinity.explanation(m.getActivityTag())), affinity.rank());
			})
			.sorted(Comparator.comparingInt(Ranked::rank).reversed())
			.map(Ranked::answer)
			.toList();
	}

	// ---- staff scheduling -----------------------------------------------------------------------------

	/** Schedules a prompt for a day, for everyone or (with a home region) as a Roots prompt. */
	@Transactional
	public ScheduledView schedule(String staffId, LocalDate date, String homeRegion, String text, String activityHint) {
		staff.require(staffId, StaffRole.MODERATOR);
		if (date.isBefore(LocalDate.now(clock).minusDays(1))) {
			throw ApiException.unprocessable("DATE_PASSED", "Pick today or a future date");
		}
		String region = normalizeRegion(homeRegion);
		DailyPrompt prompt = scheduled.save(new DailyPrompt(date, region, text.strip(),
				ActivityTags.normalize(activityHint), staffId, clock.instant()));
		audit.record(staffId, "PROMPT_SCHEDULED", "PROMPT", prompt.getId(), date + " " + (region == null ? "all" : region));
		return ScheduledView.of(prompt);
	}

	@Transactional(readOnly = true)
	public List<ScheduledView> upcoming(String staffId) {
		staff.require(staffId, StaffRole.MODERATOR);
		return scheduled.findByPromptDateGreaterThanEqualOrderByPromptDateAsc(LocalDate.now(clock).minusDays(1))
			.stream()
			.map(ScheduledView::of)
			.toList();
	}

	/** Adds a festival season: for one home region (a Roots festival) or, without one, for everyone. */
	@Transactional
	public FestivalView addFestival(String staffId, String name, String homeRegion, LocalDate startsOn,
			LocalDate endsOn, String promptText, String activityHint) {
		staff.require(staffId, StaffRole.MODERATOR);
		if (endsOn.isBefore(startsOn) || startsOn.plusDays(30).isBefore(endsOn)) {
			throw ApiException.unprocessable("INVALID_SEASON", "A season runs from 1 to 31 days");
		}
		String region = normalizeRegion(homeRegion);
		FestivalSeason season = festivals.save(new FestivalSeason(name.strip(), region, startsOn, endsOn,
				promptText.strip(), ActivityTags.normalize(activityHint), staffId, clock.instant()));
		audit.record(staffId, "FESTIVAL_ADDED", "FESTIVAL", season.getId(),
				name + " " + startsOn + ".." + endsOn + " " + (region == null ? "all" : region));
		return FestivalView.of(season);
	}

	@Transactional(readOnly = true)
	public List<FestivalView> festivals(String staffId) {
		staff.require(staffId, StaffRole.MODERATOR);
		return festivals.findByEndsOnGreaterThanEqualOrderByStartsOnAsc(LocalDate.now(clock).minusDays(1))
			.stream()
			.map(FestivalView::of)
			.toList();
	}

	@Transactional
	public void removeFestival(String staffId, String id) {
		staff.require(staffId, StaffRole.MODERATOR);
		FestivalSeason season = festivals.findById(id).orElseThrow(() -> ApiException.notFound("Festival"));
		festivals.delete(season);
		audit.record(staffId, "FESTIVAL_REMOVED", "FESTIVAL", id, season.getName());
	}

	private static String normalizeRegion(String homeRegion) {
		String region = homeRegion == null || homeRegion.isBlank() ? null : homeRegion.trim().toUpperCase();
		if (region != null && !region.matches("[A-Z]{2}-[A-Z0-9]{1,3}")) {
			throw ApiException.badRequest("INVALID_REGION", "Use a region code such as IN-KL");
		}
		return region;
	}

	public record FestivalView(String id, String name, String homeRegion, LocalDate startsOn, LocalDate endsOn,
			String promptText, String activityHint) {

		static FestivalView of(FestivalSeason f) {
			return new FestivalView(f.getId(), f.getName(), f.getHomeRegion(), f.getStartsOn(), f.getEndsOn(),
					f.getPromptText(), f.getActivityHint());
		}
	}

	private record Ranked(Answer answer, int rank) {
	}

	/**
	 * @param count capped at "9+": enough to feel the room, never a scoreboard
	 * @param answers empty until the viewer has shared a public answer
	 */
	public record TodayView(String promptKey, String text, String activityHint, boolean rootsPrompt, String festival,
			boolean answeredByYou, boolean unlocked, String answeredNearby, int fromYourHomeRegion,
			List<Answer> answers, String message) {
	}

	/** A Layer-0 answer card; {@code momentId} is the handle for opening it or sending a Signal. */
	public record Answer(String momentId, String firstName, MomentKind kind, String activity, String previewUrl,
			boolean liveCaptured, boolean fromYourHomeRegion, String whyYouSeeThis) {
	}

	public record ScheduledView(String id, LocalDate date, String homeRegion, String text, String activityHint) {

		static ScheduledView of(DailyPrompt p) {
			return new ScheduledView(p.getId(), p.getPromptDate(), p.getHomeRegion(), p.getText(), p.getActivityHint());
		}
	}
}
