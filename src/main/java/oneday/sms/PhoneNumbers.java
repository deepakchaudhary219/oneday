package oneday.sms;

import java.util.regex.Pattern;

import oneday.common.ApiException;

/** E.164 normalisation shared by phone login and trusted contacts, so a number means the same everywhere. */
public final class PhoneNumbers {

	private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");

	private PhoneNumbers() {
	}

	/** Bare 10-digit and 0-prefixed national numbers get {@code defaultCountryCode} (India: +91). */
	public static String normalize(String raw, String defaultCountryCode) {
		String digits = raw == null ? "" : raw.replaceAll("[\\s\\-().]", "");
		if (digits.startsWith("00")) {
			digits = "+" + digits.substring(2);
		}
		else if (digits.matches("0\\d{10}")) {
			digits = defaultCountryCode + digits.substring(1);
		}
		else if (digits.matches("\\d{10}")) {
			digits = defaultCountryCode + digits;
		}
		if (!E164.matcher(digits).matches()) {
			throw ApiException.badRequest("INVALID_PHONE", "Enter a mobile number, e.g. 98765 43210 or +91 98765 43210");
		}
		return digits;
	}
}
