package oneday.notify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import oneday.integrations.GoogleServiceAccount;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Firebase Cloud Messaging, HTTP v1 API ({@code oneday.push.provider=fcm}, plus {@code oneday.google.credentials-file}).
 * FCM delivers to Android directly and to iOS through APNs, so one adapter covers both platforms.
 *
 * <p>
 * Pushes carry a title, body and a small routing payload; Discretion Mode copy is applied before this point.
 * An {@code UNREGISTERED} or invalid token raises {@link InvalidTokenException} (the device is forgotten);
 * quota and server errors propagate so the outbox retries with backoff.
 */
@Component
@ConditionalOnProperty(name = "oneday.push.provider", havingValue = "fcm")
public class FcmPushSender implements PushSender {

	static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

	private final GoogleServiceAccount google;

	private final HttpClient http;

	private final JsonMapper json;

	private final String endpoint;

	public FcmPushSender(GoogleServiceAccount google, HttpClient vendorHttpClient, JsonMapper json,
			@Value("${oneday.push.fcm.endpoint:https://fcm.googleapis.com}") String endpoint) {
		this.google = google;
		this.http = vendorHttpClient;
		this.json = json;
		this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
	}

	@Override
	public void send(String pushToken, PushMessage message) {
		Map<String, Object> body = Map.of("message", Map.of("token", pushToken, "notification",
				Map.of("title", message.title(), "body", message.body()), "data",
				new LinkedHashMap<>(message.data()), "android", Map.of("priority", "high"), "apns",
				Map.of("headers", Map.of("apns-priority", "10"))));
		HttpRequest request = HttpRequest
			.newBuilder(URI.create(endpoint + "/v1/projects/" + google.projectId() + "/messages:send"))
			.timeout(Duration.ofSeconds(10))
			.header("Authorization", "Bearer " + google.accessToken(SCOPE))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
			.build();
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException ex) {
			throw new IllegalStateException("FCM unreachable", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted", ex);
		}
		int status = response.statusCode();
		if (status == 200) {
			return;
		}
		String error = response.body() == null ? "" : response.body();
		if (status == 404 || (status == 400 && (error.contains("UNREGISTERED") || error.contains("registration token")))) {
			throw new InvalidTokenException("FCM reports the token as invalid (" + status + ")");
		}
		throw new IllegalStateException("FCM answered " + status);
	}
}
