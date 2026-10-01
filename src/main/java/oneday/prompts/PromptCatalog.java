package oneday.prompts;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import oneday.profile.Profile;
import oneday.profile.ProfileService;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the one Today's Prompt a person sees: a staff-scheduled Roots prompt for their home region,
 * else a scheduled prompt for everyone, else the built-in catalogue. "Today" is the person's own local date,
 * so the prompt turns over at their midnight in every country.
 *
 * <p>
 * Prompts are written to be easy (a photo of something in front of you, not an essay), present-tense and
 * place-rooted, and they never ask for anything that reveals a routine: no home, street, room, commute or
 * workplace (docs/05-engagement-psychology.md §4.2).
 */
@Component
public class PromptCatalog {

	static final String CATALOG_PREFIX = "catalog:";

	static final List<Entry> CATALOG = List.of(
			new Entry("Show us the view from where you are right now", null),
			new Entry("Your chai or coffee of the day", "coffee"),
			new Entry("Something that tastes like home", "food"),
			new Entry("The best street food you can find nearby", "food"),
			new Entry("Today's sky", null),
			new Entry("Something that made you smile today", null),
			new Entry("A small win from today", null),
			new Entry("What you're reading, playing or learning right now", "learning"),
			new Entry("The soundtrack of your evening (show where you're listening)", "music"),
			new Entry("A corner of this city most people walk past", "walk"),
			new Entry("Something only locals would recognise", null),
			new Entry("Your workout, walk or stretch today", "fitness"),
			new Entry("Something green you found today", "nature"),
			new Entry("A piece of art, a mural or a sign that caught your eye", "art"),
			new Entry("What's on your plate tonight", "food"),
			new Entry("A tiny act of kindness you saw (or did)", null),
			new Entry("Your favourite spot to think, as long as it's a public one", null),
			new Entry("Something old that still works", null),
			new Entry("The colour of your day", null),
			new Entry("A game, match or sport you're watching or playing", "sports"),
			new Entry("The cutest animal you met today", "animals"),
			new Entry("A local shop you'd tell a friend about", null),
			new Entry("Something you made with your hands", "craft"),
			new Entry("Where the weekend is taking you", "trek"),
			new Entry("A view worth the climb", "trek"),
			new Entry("A sweet you'd never say no to", "food"),
			new Entry("Your language in the wild: a sign, menu or poster", null),
			new Entry("The festival, event or crowd near you today", "events"),
			new Entry("Golden hour, wherever you are", null),
			new Entry("Something you're grateful for today", null));

	static final String FESTIVAL_PREFIX = "f:";

	private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private final DailyPromptRepository scheduled;

	private final FestivalSeasonRepository festivals;

	private final ProfileService profiles;

	private final Clock clock;

	public PromptCatalog(DailyPromptRepository scheduled, FestivalSeasonRepository festivals, ProfileService profiles,
			Clock clock) {
		this.scheduled = scheduled;
		this.festivals = festivals;
		this.profiles = profiles;
		this.clock = clock;
	}

	/**
	 * Precedence: a prompt scheduled for the person's home region on that date, then a festival season for
	 * their region, then a prompt scheduled for everyone, then a festival for everyone, then the catalogue.
	 */
	@Transactional(readOnly = true)
	public Prompt todayFor(String userId) {
		Optional<Profile> profile = profiles.find(userId);
		ZoneId zone = profile.map(p -> ZoneId.of(p.getTimeZone())).orElse(ZoneId.of("UTC"));
		LocalDate date = LocalDate.now(clock.withZone(zone));
		String region = profile.map(Profile::getHomeRegion).orElse(null);
		List<FestivalSeason> active = festivals.findActiveOn(date);
		if (region != null) {
			Optional<DailyPrompt> roots = scheduled.findFirstByPromptDateAndHomeRegionOrderByCreatedAtDesc(date, region);
			if (roots.isPresent()) {
				return scheduledPrompt(roots.get(), date);
			}
			Optional<FestivalSeason> rootsFestival = active.stream().filter(f -> region.equals(f.getHomeRegion())).findFirst();
			if (rootsFestival.isPresent()) {
				return festivalPrompt(rootsFestival.get(), date);
			}
		}
		Optional<DailyPrompt> everyone = scheduled.findFirstByPromptDateAndHomeRegionIsNullOrderByCreatedAtDesc(date);
		if (everyone.isPresent()) {
			return scheduledPrompt(everyone.get(), date);
		}
		Optional<FestivalSeason> festival = active.stream().filter(f -> f.getHomeRegion() == null).findFirst();
		if (festival.isPresent()) {
			return festivalPrompt(festival.get(), date);
		}
		// Same catalogue prompt for everyone on the same local date, so a city answers together.
		Entry entry = CATALOG.get((int) Math.floorMod(date.toEpochDay() * 7, CATALOG.size()));
		return new Prompt(CATALOG_PREFIX + date, date, entry.text(), entry.activityHint(), false, null);
	}

	/** Whether {@code key} is this person's prompt today (answers are accepted only on the day). */
	public boolean isTodays(String userId, String key) {
		return key != null && key.equals(todayFor(userId).key());
	}

	/** The festival a prompt key belongs to, if it is a festival prompt (for Story Map labels). */
	@Transactional(readOnly = true)
	public Optional<String> festivalOf(String key) {
		if (key == null || !key.startsWith(FESTIVAL_PREFIX)) {
			return Optional.empty();
		}
		int end = key.indexOf(':', FESTIVAL_PREFIX.length());
		String id = end < 0 ? key.substring(FESTIVAL_PREFIX.length()) : key.substring(FESTIVAL_PREFIX.length(), end);
		return festivals.findById(id).map(FestivalSeason::getName);
	}

	private static Prompt scheduledPrompt(DailyPrompt p, LocalDate date) {
		return new Prompt(p.getId(), date, p.getText(), p.getActivityHint(), p.getHomeRegion() != null, null);
	}

	/** One key per festival per day, so each day of a season is its own prompt (and fits the 48-char column). */
	private static Prompt festivalPrompt(FestivalSeason f, LocalDate date) {
		return new Prompt(FESTIVAL_PREFIX + f.getId() + ":" + COMPACT_DATE.format(date), date, f.getPromptText(),
				f.getActivityHint(), f.getHomeRegion() != null, f.getName());
	}

	/**
	 * @param key the handle a moment uses to answer it
	 * @param roots true for a prompt aimed at the person's home region
	 * @param festival the festival season this prompt belongs to, if any
	 */
	public record Prompt(String key, LocalDate date, String text, String activityHint, boolean roots, String festival) {
	}

	record Entry(String text, String activityHint) {
	}
}
