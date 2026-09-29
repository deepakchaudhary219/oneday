package oneday.sms;

/**
 * Outbound SMS port. Indian production senders must use TRAI DLT-registered sender headers and message
 * templates; the adapter owns that mapping.
 */
public interface SmsSender {

	void send(String e164Phone, String message);
}
