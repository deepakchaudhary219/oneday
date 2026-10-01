package oneday.attestation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import oneday.integrations.GoogleServiceAccount;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Play Integrity ({@code oneday.attestation.provider=play-integrity}): the server decodes the app's
 * integrity token with Google and accepts it only if the request names our package, the app is the one Play
 * recognises, the device meets basic device integrity, and the token is fresh. iOS (App Attest) is the next
 * adapter behind the same port.
 */
@Component
@ConditionalOnProperty(name = "oneday.attestation.provider", havingValue = "play-integrity")
class PlayIntegrityAttestor implements DeviceAttestor {

	static final String SCOPE = "https://www.googleapis.com/auth/playintegrity";

	static final Duration MAX_AGE = Duration.ofMinutes(5);

	private static final Logger log = LoggerFactory.getLogger(PlayIntegrityAttestor.class);

	private final GoogleServiceAccount google;

	private final HttpClient http;

	private final JsonMapper json;

	private final Clock clock;

	private final String packageName;

	private final String endpoint;

	PlayIntegrityAttestor(GoogleServiceAccount google, HttpClient vendorHttpClient, JsonMapper json, Clock clock,
			@Value("${oneday.attestation.android-package}") String packageName,
			@Value("${oneday.attestation.play-endpoint:https://playintegrity.googleapis.com}") String endpoint) {
		this.google = google;
		this.http = vendorHttpClient;
		this.json = json;
		this.clock = clock;
		this.packageName = packageName;
		this.endpoint = endpoint;
	}

	@Override
	public Verdict verify(String token, String action) {
		try {
			HttpResponse<String> response = http.send(HttpRequest
				.newBuilder(URI.create(endpoint + "/v1/" + packageName + ":decodeIntegrityToken"))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Bearer " + google.accessToken(SCOPE))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("integrity_token", token))))
				.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				log.warn("Play Integrity answered {}", response.statusCode());
				return Verdict.FAILED;
			}
			JsonNode payload = json.readTree(response.body()).path("tokenPayloadExternal");
			boolean rightApp = packageName.equals(payload.path("requestDetails").path("requestPackageName").asText())
					&& "PLAY_RECOGNIZED".equals(payload.path("appIntegrity").path("appRecognitionVerdict").asText());
			boolean device = false;
			for (JsonNode v : payload.path("deviceIntegrity").path("deviceRecognitionVerdict")) {
				device |= "MEETS_DEVICE_INTEGRITY".equals(v.asText()) || "MEETS_STRONG_INTEGRITY".equals(v.asText());
			}
			long millis = payload.path("requestDetails").path("timestampMillis").asLong(0);
			boolean fresh = millis > 0 && Duration.between(Instant.ofEpochMilli(millis), clock.instant()).abs()
				.compareTo(MAX_AGE) <= 0;
			return rightApp && device && fresh ? Verdict.TRUSTED : Verdict.FAILED;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Play Integrity unreachable", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted", ex);
		}
	}
}
