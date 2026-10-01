package oneday.notify;

import java.util.Map;

/** Outbound push port (FCM/APNs adapter in production). */
public interface PushSender {

	/**
	 * @throws InvalidTokenException when the provider says the token is gone for good (app uninstalled); the
	 * device is then forgotten instead of retried. Any other exception is treated as transient.
	 */
	void send(String pushToken, PushMessage message);

	/** The provider reports the token as permanently invalid. */
	class InvalidTokenException extends RuntimeException {

		public InvalidTokenException(String message) {
			super(message);
		}
	}

	/**
	 * @param data non-visible payload for the app to route the tap (never personal content)
	 */
	record PushMessage(String title, String body, Map<String, String> data) {
	}
}
