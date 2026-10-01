package oneday.prompts;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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

	private final DailyPromptRepository scheduled;

	private final ProfileService profiles;

	private final Clock clock;

	public PromptCatalog(DailyPromptRepository scheduled, ProfileService profiles, Clock clock) {
		this.scheduled = scheduled;
		this.profiles = profiles;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Prompt todayFor(String userId) {
		Optional<Profile> profile = profiles.find(userId);
		ZoneId zone = profile.map(p -> ZoneId.of(p.getTimeZone())).orElse(ZoneId.of("UTC"));
		LocalDate date = LocalDate.now(clock.withZone(zone));
		String region = profile.map(Profile::getHomeRegion).orElse(null);
		Optional<DailyPrompt> chosen = region == null ? Optional.empty()
				: scheduled.findFirstByPromptDateAndHomeRegionOrderByCreatedAtDesc(date, region);
		if (chosen.isEmpty()) {
			chosen = scheduled.findFirstByPromptDateAndHomeRegionIsNullOrderByCreatedAtDesc(date);
		}
		if (chosen.isPresent()) {
			DailyPrompt p = chosen.get();
			return new Prompt(p.getId(), date, p.getText(), p.getActivityHint(), p.getHomeRegion() != null);
		}
		// Same catalogue prompt for everyone on the same local date, so a city answers together.
		Entry entry = CATALOG.get((int) Math.floorMod(date.toEpochDay() * 7, CATALOG.size()));
		return new Prompt(CATALOG_PREFIX + date, date, entry.text(), entry.activityHint(), false);
	}

	/** Whether {@code key} is this person's prompt today (answers are accepted only on the day). */
	public boolean isTodays(String userId, String key) {
		return key != null && key.equals(todayFor(userId).key());
	}

	/**
	 * @param key the handle a moment uses to answer it
	 * @param roots true for a prompt aimed at the person's home region
	 */
	public record Prompt(String key, LocalDate date, String text, String activityHint, boolean roots) {
	}

	record Entry(String text, String activityHint) {
	}
}
