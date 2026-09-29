package oneday.notify;

import java.util.Map;

/** Outbound push port (FCM/APNs adapter in production). */
public interface PushSender {

	void send(String pushToken, PushMessage message);

	/**
	 * @param data non-visible payload for the app to route the tap (never personal content)
	 */
	record PushMessage(String title, String body, Map<String, String> data) {
	}
}
