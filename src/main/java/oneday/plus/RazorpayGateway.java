package oneday.plus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Razorpay Subscriptions ({@code oneday.plus.provider=razorpay}): recurring payments with UPI Autopay (no card
 * needed) and cards. The app opens Razorpay Checkout with the subscription id; the mandate and every renewal are
 * then reported by webhook, verified with HMAC-SHA256 over the raw body in constant time.
 */
@Component
@ConditionalOnProperty(name = "oneday.plus.provider", havingValue = "razorpay")
class RazorpayGateway implements PaymentGateway {

	private final PlusProperties.Razorpay settings;

	private final HttpClient http;

	private final JsonMapper json;

	private final String endpoint;

	RazorpayGateway(PlusProperties properties, HttpClient vendorHttpClient, JsonMapper json) {
		this.settings = properties.razorpay();
		if (settings == null || blank(settings.keyId()) || blank(settings.keySecret()) || blank(settings.planId())
				|| blank(settings.webhookSecret())) {
			throw new IllegalStateException("oneday.plus.razorpay key-id, key-secret, plan-id and webhook-secret are required");
		}
		this.http = vendorHttpClient;
		this.json = json;
		this.endpoint = blank(settings.endpoint()) ? "https://api.razorpay.com" : settings.endpoint();
	}

	@Override
	public String name() {
		return "razorpay";
	}

	@Override
	public Checkout createSubscription(String localReference) {
		JsonNode created = post("/v1/subscriptions", Map.of("plan_id", settings.planId(), "total_count", 120,
				"customer_notify", 1, "notes", Map.of("oneday_ref", localReference)));
		return new Checkout(created.get("id").asText(), settings.keyId(),
				created.has("short_url") ? created.get("short_url").asText() : null);
	}

	@Override
	public void cancelAtCycleEnd(String providerSubscriptionId) {
		post("/v1/subscriptions/" + providerSubscriptionId + "/cancel", Map.of("cancel_at_cycle_end", 1));
	}

	@Override
	public boolean verifyWebhook(String rawBody, String signature) {
		if (signature == null || rawBody == null) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(settings.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
			return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
					signature.trim().getBytes(StandardCharsets.US_ASCII));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private JsonNode post(String path, Object body) {
		String basic = Base64.getEncoder()
			.encodeToString((settings.keyId() + ":" + settings.keySecret()).getBytes(StandardCharsets.UTF_8));
		try {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(endpoint + path))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Basic " + basic)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
				.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException("Razorpay answered " + response.statusCode());
			}
			return json.readTree(response.body());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Razorpay unreachable", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted", ex);
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
