package oneday.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What two people genuinely share. Drives Narrative Match Explanations (blueprint §20.7): a sentence,
 * never a percentage. {@link #rank()} orders candidates internally and is never exposed.
 */
public record Affinity(
		Set<String> sharedValues,
		Set<String> sharedActivities,
		Set<String> sharedLanguages,
		String sharedHomeRegion,
		boolean datingCompatible) {

	public static Affinity between(Profile viewer, Profile other) {
		String region = viewer.getHomeRegion() != null && viewer.getHomeRegion().equals(other.getHomeRegion())
				? viewer.getHomeRegion() : null;
		return new Affinity(intersect(viewer.getValues(), other.getValues()),
				intersect(viewer.getActivities(), other.getActivities()),
				intersect(viewer.getLanguages(), other.getLanguages()), region, viewer.datingCompatibleWith(other));
	}

	public boolean rootsMatch() {
		return sharedHomeRegion != null;
	}

	public int rank() {
		return 2 * sharedValues.size() + 2 * sharedActivities.size() + sharedLanguages.size() + (rootsMatch() ? 3 : 0)
				+ (datingCompatible ? 1 : 0);
	}

	/** e.g. "You both value kindness and adventure · both up for trek · also from IN-KL". */
	public String explanation(String momentActivity) {
		List<String> parts = new ArrayList<>();
		if (!sharedValues.isEmpty()) {
			List<String> labels = sharedValues.stream().limit(2).map(v -> CoreValue.valueOf(v).label()).toList();
			parts.add("you both value " + String.join(" and ", labels));
		}
		String activity = momentActivity != null && sharedActivities.contains(momentActivity) ? momentActivity
				: sharedActivities.stream().findFirst().orElse(null);
		if (activity != null) {
			parts.add("both up for " + activity);
		}
		if (rootsMatch()) {
			parts.add("also from " + sharedHomeRegion);
		}
		if (!sharedLanguages.isEmpty()) {
			List<String> names = sharedLanguages.stream()
				.limit(2)
				.map(code -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH))
				.toList();
			parts.add("you both speak " + String.join(" and ", names));
		}
		if (parts.isEmpty()) {
			return "Nearby and sharing a live moment";
		}
		String sentence = String.join(" · ", parts);
		return Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1);
	}

	private static Set<String> intersect(Set<String> a, Set<String> b) {
		Set<String> result = new LinkedHashSet<>(a);
		result.retainAll(b);
		return result;
	}
}
