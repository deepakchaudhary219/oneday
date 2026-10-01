package oneday.plus;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import oneday.integrations.FakeVendorServer;
import oneday.integrations.FakeVendorServer.Response;

import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;

/** The Razorpay adapter against a local fake: Basic auth, plan id, cancel at cycle end, constant-time signatures. */
class RazorpayGatewayTest {

	@Test
	void createsAndCancelsSubscriptionsAndVerifiesWebhookSignatures() throws Exception {
		try (FakeVendorServer razorpay = new FakeVendorServer()) {
			String basic = "Basic " + Base64.getEncoder().encodeToString("rzp_key:rzp_secret".getBytes(StandardCharsets.UTF_8));
			razorpay.route("/v1/subscriptions", call -> basic.equals(call.headers().get("authorization"))
					? new Response(200, "{\"id\":\"sub_123\",\"status\":\"created\",\"short_url\":\"https://rzp.io/i/x\"}")
					: new Response(401, "{}"));
			razorpay.route("/v1/subscriptions/sub_123/cancel", call -> new Response(200, "{\"status\":\"active\"}"));
			PlusProperties properties = new PlusProperties("razorpay", "x", Duration.ofDays(3), 5, 15, 2, 5,
					new PlusProperties.Razorpay("rzp_key", "rzp_secret", "plan_abc", "whsec", razorpay.baseUrl()));
			RazorpayGateway gateway = new RazorpayGateway(properties, HttpClient.newHttpClient(),
					JsonMapper.builder().build());

			PaymentGateway.Checkout checkout = gateway.createSubscription("ref-1");
			assertThat(checkout.providerSubscriptionId()).isEqualTo("sub_123");
			assertThat(checkout.checkoutKey()).isEqualTo("rzp_key"); // public key only, never the secret
			assertThat(razorpay.calls.get(0).body()).contains("\"plan_id\":\"plan_abc\"", "\"oneday_ref\":\"ref-1\"");
			gateway.cancelAtCycleEnd("sub_123");
			assertThat(razorpay.calls.get(1).body()).contains("\"cancel_at_cycle_end\":1");

			String body = "{\"event\":\"subscription.charged\"}";
			assertThat(gateway.verifyWebhook(body, DevPaymentGateway.sign(body, "whsec"))).isTrue();
			assertThat(gateway.verifyWebhook(body + " ", DevPaymentGateway.sign(body, "whsec"))).isFalse();
			assertThat(gateway.verifyWebhook(body, null)).isFalse();
		}
	}
}
