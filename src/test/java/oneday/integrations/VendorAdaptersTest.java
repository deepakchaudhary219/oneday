package oneday.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

import oneday.integrations.FakeVendorServer.Response;
import oneday.notify.FcmPushSender;
import oneday.notify.PushSender.InvalidTokenException;
import oneday.notify.PushSender.PushMessage;
import oneday.sms.Msg91Properties;
import oneday.sms.Msg91SmsSender;
import oneday.sms.SmsTemplate;

import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The FCM and MSG91 adapters against a local fake of the vendors (and of Google's OAuth endpoint). */
class VendorAdaptersTest {

	private final JsonMapper json = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newHttpClient();

	private FakeVendorServer vendor;

	@TempDir
	Path dir;

	@BeforeEach
	void start() throws Exception {
		vendor = new FakeVendorServer();
	}

	@AfterEach
	void stop() {
		vendor.close();
	}

	@Test
	void fcmSendsWithACachedOAuthTokenAndClassifiesErrors() throws Exception {
		GoogleServiceAccount google = new GoogleServiceAccount(vendor.credentials(dir), http, json, Clock.systemUTC());
		vendor.route("/v1/projects/oneday-test/messages:send", call -> {
			if (!"Bearer tok-1".equals(call.headers().get("authorization"))) {
				return new Response(401, "{}");
			}
			if (call.body().contains("\"gone-token\"")) {
				return new Response(404, "{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}");
			}
			return call.body().contains("\"busy-token\"") ? new Response(503, "{}") : new Response(200, "{\"name\":\"m1\"}");
		});
		FcmPushSender fcm = new FcmPushSender(google, http, json, vendor.baseUrl());
		PushMessage message = new PushMessage("Update", "You have an update", Map.of("open", "pulse"));

		fcm.send("device-1", message);
		fcm.send("device-2", message);
		assertThat(vendor.tokenRequests.get()).isEqualTo(1); // the access token is reused
		assertThat(vendor.calls.stream().filter(c -> c.path().endsWith("messages:send")).findFirst().orElseThrow().body())
			.contains("\"token\":\"device-1\"", "\"title\":\"Update\"", "\"open\":\"pulse\"", "\"priority\":\"high\"");

		assertThatThrownBy(() -> fcm.send("gone-token", message)).isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> fcm.send("busy-token", message)).isInstanceOf(IllegalStateException.class)
			.isNotInstanceOf(InvalidTokenException.class);
	}

	@Test
	void msg91SendsOnlyRegisteredTemplatesWithVariables() {
		vendor.route("/api/v5/flow", call -> "secret-key".equals(call.headers().get("authkey"))
				? new Response(200, "{\"type\":\"success\"}") : new Response(401, "{\"type\":\"error\"}"));
		Msg91SmsSender sms = new Msg91SmsSender(
				new Msg91Properties("secret-key", vendor.baseUrl(), Map.of("OTP", "tpl-otp")), http, json);

		sms.send("+919876543210", SmsTemplate.OTP, Map.of("code", "123456", "minutes", "5"), "Your code is 123456");
		String body = vendor.calls.get(0).body();
		assertThat(body).contains("\"template_id\":\"tpl-otp\"", "\"mobiles\":\"919876543210\"", "\"code\":\"123456\"")
			.doesNotContain("Your code is");

		assertThatThrownBy(() -> sms.send("+919876543210", "free text")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> sms.send("+919876543210", SmsTemplate.SAFETY_ALERT, Map.of(), "x"))
			.hasMessageContaining("No MSG91 template configured for SAFETY_ALERT");
	}
}
