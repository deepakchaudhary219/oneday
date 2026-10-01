package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end encrypted chat, server side: key directory, exact device coverage, per-device inboxes that empty
 * on delivery, no downgrade, devices tied to sign-in sessions, and franked reports with verified evidence.
 * The "ciphertext" here is opaque random bytes: the server must never need to understand it.
 */
@SpringBootTest
class E2eeChatIntegrationTest extends ApiTestSupport {

	private static final SecureRandom RANDOM = new SecureRandom();

	@Test
	void encryptedMessagesReachEveryDeviceAndOnlyThem() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connection = connect(asha, ravi);
		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");
		String ravi2 = signIn(ravi); // Ravi's second phone: another sign-in session

		int ashaPhone = register(asha, 3);
		postAs(asha, "/e2ee/devices", device(1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEVICE_EXISTS"));
		send(asha, conversation, ashaPhone, List.of()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("E2EE_UNAVAILABLE"));
		int raviPhone = register(ravi, 2);
		int raviTablet = register(ravi2, 0);
		assertThat(raviTablet).isNotEqualTo(raviPhone);

		// Bundles come per connection; each one-time prekey is handed out once.
		String bundles = body(getAs(asha, "/e2ee/connections/" + connection + "/bundles").andExpect(status().isOk()));
		assertThat((List<Integer>) JsonPath.read(bundles, "$[*].deviceId")).containsExactlyInAnyOrder(raviPhone, raviTablet);
		getAs(asha, "/e2ee/connections/" + connection + "/bundles");
		getAs(asha, "/e2ee/connections/" + connection + "/bundles")
			.andExpect(jsonPath("$[?(@.deviceId==" + raviPhone + ")].oneTimePreKey").value(contains((Object) null)));
		getAs(ravi, "/e2ee/devices").andExpect(jsonPath("$[?(@.deviceId==" + raviPhone + ")].oneTimePreKeysLeft").value(contains(0)));

		// Missing one of Ravi's devices: refused, and told which.
		send(asha, conversation, ashaPhone, List.of(to(false, raviPhone))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEVICE_LIST_MISMATCH"))
			.andExpect(jsonPath("$.missing.them", containsInAnyOrder(raviTablet)));

		String text = "you're so stupid";
		byte[] frankingKey = random(32);
		byte[] cipherForPhone = random(80);
		String sent = body(send(asha, conversation, ashaPhone, List.of(to(false, raviPhone, cipherForPhone), to(false, raviTablet)),
				hmac(frankingKey, text)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.encrypted").value(true))
			.andExpect(jsonPath("$.body").doesNotExist()));
		String messageId = JsonPath.read(sent, "$.id");

		// No downgrade once a conversation is encrypted.
		postAs(asha, "/conversations/" + conversation + "/messages", "{\"body\":\"hello in clear\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("E2EE_REQUIRED"));
		getAs(ravi, "/conversations/" + conversation + "/messages")
			.andExpect(jsonPath("$[0].encrypted").value(true))
			.andExpect(jsonPath("$[0].body").doesNotExist());

		// Each device reads only its own inbox, from its own sign-in; delivered envelopes are deleted.
		String inbox = body(getAs(ravi, "/e2ee/devices/" + raviPhone + "/inbox").andExpect(jsonPath("$", hasSize(1))));
		assertThat(Base64.getDecoder().decode((String) JsonPath.read(inbox, "$[0].ciphertext"))).isEqualTo(cipherForPhone);
		assertThat((Boolean) JsonPath.read(inbox, "$[0].mine")).isFalse();
		assertThat((String) JsonPath.read(inbox, "$[0].type")).isEqualTo("PREKEY");
		getAs(ravi, "/e2ee/devices/" + raviTablet + "/inbox").andExpect(status().isForbidden());
		getAs(ravi2, "/e2ee/devices/" + raviTablet + "/inbox").andExpect(jsonPath("$", hasSize(1)));
		String envelopeId = JsonPath.read(inbox, "$[0].envelopeId");
		postAs(ravi, "/e2ee/devices/" + raviPhone + "/inbox/ack", "{\"envelopeIds\":[\"" + envelopeId + "\"]}")
			.andExpect(jsonPath("$.deleted").value(1));
		getAs(ravi, "/e2ee/devices/" + raviPhone + "/inbox").andExpect(jsonPath("$", hasSize(0)));

		// A franked report proves exactly what was sent; a doctored one doesn't verify.
		String report = "/conversations/" + conversation + "/messages/" + messageId + "/report";
		postAs(ravi, report, reportBody("you're so smart", frankingKey)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("FRANKING_MISMATCH"));
		postAs(asha, report, reportBody(text, frankingKey)).andExpect(status().isNotFound());
		postAs(ravi, report, reportBody(text, frankingKey)).andExpect(status().isCreated());
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		getAs(moderator, "/staff/reports").andExpect(jsonPath("$[0].verifiedEvidence").value(text))
			.andExpect(jsonPath("$[0].targetType").value("CONNECTION"));
	}

	@Test
	void signingOutAPhoneUnlinksItsDeviceAndErasureRemovesKeys() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connection = connect(asha, ravi);
		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");
		String ravi2 = signIn(ravi);
		int ashaPhone = register(asha, 1);
		int raviPhone = register(ravi, 1);
		register(ravi2, 1);

		postAs(ravi2, "/auth/logout", null).andExpect(status().isNoContent());
		getAs(asha, "/e2ee/connections/" + connection + "/bundles").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].deviceId").value(raviPhone));
		send(asha, conversation, ashaPhone, List.of(to(false, raviPhone))).andExpect(status().isCreated());

		getAs(ravi, "/privacy/export").andExpect(jsonPath("$.secureChatDevices", hasSize(1)));
		deleteAs(ravi, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from e2ee_devices where user_id = ?", Integer.class,
				userIdOf(ravi))).isZero();
		assertThat(jdbc.queryForObject("select count(*) from e2ee_envelopes", Integer.class)).isZero();
	}

	@Test
	void theAppCanMirrorTheEmpathyCheckOnDevice() throws Exception {
		String asha = verifiedUser("Asha");
		String etag = getAs(asha, "/empathy/lexicon").andExpect(status().isOk())
			.andExpect(header().string("Content-Type", startsWith("text/plain")))
			.andReturn()
			.getResponse()
			.getHeader("ETag");
		assertThat(etag).isNotBlank();
		perform(get("/empathy/lexicon").header("If-None-Match", etag), asha, null).andExpect(status().isNotModified());
	}

	// ---- helpers -----------------------------------------------------------------------------------------

	private int register(String token, int oneTimePreKeys) throws Exception {
		return JsonPath.read(body(postAs(token, "/e2ee/devices", device(oneTimePreKeys)).andExpect(status().isCreated())),
				"$.deviceId");
	}

	private static String device(int oneTimePreKeys) {
		StringBuilder prekeys = new StringBuilder();
		for (int i = 0; i < oneTimePreKeys; i++) {
			prekeys.append(i == 0 ? "" : ",").append("{\"keyId\":").append(i + 1).append(",\"publicKey\":\"")
				.append(b64(random(33))).append("\"}");
		}
		return """
				{"registrationId":%d,"identityKey":"%s",
				 "signedPreKey":{"keyId":1,"publicKey":"%s","signature":"%s"},"oneTimePreKeys":[%s]}
				""".formatted(1 + RANDOM.nextInt(16000), b64(random(33)), b64(random(33)), b64(random(64)), prekeys);
	}

	private record Env(boolean toSelf, int deviceId, byte[] ciphertext) {
	}

	private static Env to(boolean toSelf, int deviceId) {
		return new Env(toSelf, deviceId, random(64));
	}

	private static Env to(boolean toSelf, int deviceId, byte[] ciphertext) {
		return new Env(toSelf, deviceId, ciphertext);
	}

	private ResultActions send(String token, String conversation, int device,
			List<Env> envelopes) throws Exception {
		return send(token, conversation, device, envelopes, random(32));
	}

	private ResultActions send(String token, String conversation, int device,
			List<Env> envelopes, byte[] commitment) throws Exception {
		clock.advance(Duration.ofSeconds(2));
		StringBuilder json = new StringBuilder();
		for (Env e : envelopes) {
			json.append(json.isEmpty() ? "" : ",")
				.append("{\"toSelf\":%s,\"deviceId\":%d,\"type\":\"PREKEY\",\"ciphertext\":\"%s\"}".formatted(e.toSelf(),
						e.deviceId(), b64(e.ciphertext())));
		}
		return postAs(token, "/conversations/" + conversation + "/encrypted",
				"{\"senderDevice\":%d,\"commitment\":\"%s\",\"envelopes\":[%s]}".formatted(device, b64(commitment), json));
	}

	private static String reportBody(String plaintext, byte[] key) {
		return "{\"category\":\"HARASSMENT\",\"plaintext\":\"%s\",\"frankingKey\":\"%s\"}".formatted(plaintext, b64(key));
	}

	private static byte[] hmac(byte[] key, String text) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] random(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	private static String b64(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}
}
