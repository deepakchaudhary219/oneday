package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** OneDay Plus: honest pricing, signed idempotent webhooks, convenience-only entitlements, erasure-safe records. */
@TestPropertySource(properties = "oneday.plus.free-max-radius-km=5")
class PlusIntegrationTest extends ApiTestSupport {

	@Test
	void plusIsBoughtWithAVerifiedWebhookAndOnlyAddsConvenience() throws Exception {
		String asha = verifiedUser("Asha");
		getAs(asha, "/plus").andExpect(jsonPath("$.plus").value(false))
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.neverSold", hasItem("Seeing who signalled you")))
			.andExpect(jsonPath("$.neverSold[0]", containsString("Safety features")));
		radius(asha, 10).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("Plus")));

		String subscription = JsonPath.read(body(postAs(asha, "/plus/subscribe", null).andExpect(status().isOk())),
				"$.subscriptionId");
		long periodEnd = clock.instant().plus(Duration.ofDays(30)).getEpochSecond();
		String activated = webhookBody("subscription.activated", subscription, periodEnd);

		webhook(activated, "not-a-signature", "evt_1").andExpect(status().isUnauthorized());
		getAs(asha, "/plus").andExpect(jsonPath("$.plus").value(false));
		webhook(activated, sign(activated), "evt_1").andExpect(status().isOk());
		getAs(asha, "/plus").andExpect(jsonPath("$.plus").value(true)).andExpect(jsonPath("$.status").value("ACTIVE"));
		radius(asha, 10).andExpect(status().isOk());

		// A retried delivery is acknowledged and ignored, even if its body differs.
		String halted = webhookBody("subscription.halted", subscription, periodEnd);
		webhook(halted, sign(halted), "evt_1").andExpect(status().isOk());
		getAs(asha, "/plus").andExpect(jsonPath("$.status").value("ACTIVE"));
		postAs(asha, "/plus/subscribe", null).andExpect(status().isConflict());

		// Cancelling keeps what was paid for; afterwards the free limits apply again.
		postAs(asha, "/plus/cancel", null).andExpect(jsonPath("$.cancelAtPeriodEnd").value(true))
			.andExpect(jsonPath("$.plus").value(true));
		clock.advance(Duration.ofDays(34));
		asha = signIn(asha);
		getAs(asha, "/plus").andExpect(jsonPath("$.plus").value(false));
		radius(asha, 10).andExpect(status().isBadRequest());

		// Erasure keeps the payment record (tax law) but detaches it from the person.
		deleteAs(asha, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select user_id from subscriptions", String.class)).isEqualTo("erased");
	}

	private ResultActions radius(String token, int km) throws Exception {
		return perform(patch("/profile/me"), token, "{\"discoveryRadiusKm\":" + km + "}");
	}

	private ResultActions webhook(String body, String signature, String eventId) throws Exception {
		return mvc.perform(post("/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
			.header("X-Razorpay-Signature", signature)
			.header("X-Razorpay-Event-Id", eventId)
			.content(body));
	}

	private static String webhookBody(String event, String subscriptionId, long currentEnd) {
		return """
				{"entity":"event","event":"%s","payload":{"subscription":{"entity":{"id":"%s","status":"active","current_end":%d}}}}"""
			.formatted(event, subscriptionId, currentEnd);
	}

	private static String sign(String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
	}
}
