package oneday.profile;

import java.util.Locale;
import java.util.regex.Pattern;

import oneday.common.ApiException;

/** Normalises activity tags ("Trek ", "trek") so shared activities match across people. */
public final class ActivityTags {

	private static final Pattern VALID = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} '&-]{0,29}");

	private ActivityTags() {
	}

	/** Returns the normalised tag, {@code null} for blank input, or throws for invalid tags. */
	public static String normalize(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String tag = raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		if (!VALID.matcher(tag).matches()) {
			throw ApiException.badRequest("INVALID_ACTIVITY",
					"Activities are 1-30 letters, digits, spaces, ' & or -");
		}
		return tag;
	}
}
