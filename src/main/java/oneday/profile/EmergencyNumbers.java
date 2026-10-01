package oneday.profile;

import java.util.Locale;
import java.util.Map;

/**
 * The number to show on every safety surface, by country (ISO 3166-1 alpha-2). The app is global from day
 * one, so "call 112" must be right wherever someone is. 112 also works from most GSM phones, which makes it
 * the fallback.
 */
public final class EmergencyNumbers {

	private static final String FALLBACK = "112";

	private static final Map<String, String> BY_COUNTRY = Map.ofEntries(Map.entry("IN", "112"),
			Map.entry("US", "911"), Map.entry("CA", "911"), Map.entry("MX", "911"), Map.entry("GB", "999"),
			Map.entry("IE", "112"), Map.entry("AU", "000"), Map.entry("NZ", "111"), Map.entry("SG", "999"),
			Map.entry("MY", "999"), Map.entry("AE", "999"), Map.entry("SA", "911"), Map.entry("JP", "110"),
			Map.entry("KR", "112"), Map.entry("CN", "110"), Map.entry("BR", "190"), Map.entry("ZA", "10111"),
			Map.entry("PH", "911"), Map.entry("ID", "112"), Map.entry("BD", "999"), Map.entry("PK", "15"),
			Map.entry("LK", "119"), Map.entry("NP", "100"));

	private EmergencyNumbers() {
	}

	public static String forCountry(String countryCode) {
		return countryCode == null ? FALLBACK : BY_COUNTRY.getOrDefault(countryCode.toUpperCase(Locale.ROOT), FALLBACK);
	}
}
