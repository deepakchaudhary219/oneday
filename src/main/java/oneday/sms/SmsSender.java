package oneday.sms;

import java.util.Map;

/**
 * Outbound SMS port. Indian production senders must use TRAI DLT-registered sender headers and message
 * templates; the adapter owns that mapping.
 */
public interface SmsSender {

	void send(String e164Phone, String message);

	/**
	 * Sends a registered template. {@code text} is the fully rendered message for adapters that send free text
	 * (dev); DLT adapters send the template id and {@code variables} instead.
	 */
	default void send(String e164Phone, SmsTemplate template, Map<String, String> variables, String text) {
		send(e164Phone, text);
	}
}
