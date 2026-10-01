package oneday.sms;

import java.util.List;

/**
 * The SMS the product ever sends. India's TRAI DLT rules require every commercial SMS to match a registered
 * template, so messages are a template id plus variables rather than free text. The adapter maps each template
 * to the provider's registered id; {@link #variables} is the order the template expects.
 */
public enum SmsTemplate {

	/** "Your OneDay code is {code}. It expires in {minutes} minutes. Never share it with anyone." */
	OTP(List.of("code", "minutes")),
	/** "{name} added you as their trusted contact for a meet-up on OneDay: {place}, {when}. ..." */
	TRUSTED_CONTACT(List.of("name", "place", "when", "url", "emergency")),
	/** "OneDay safety alert: {name} {what} during their meet-up at {place}. ..." */
	SAFETY_ALERT(List.of("name", "what", "place", "emergency"));

	private final List<String> variables;

	SmsTemplate(List<String> variables) {
		this.variables = variables;
	}

	public List<String> variables() {
		return variables;
	}
}
