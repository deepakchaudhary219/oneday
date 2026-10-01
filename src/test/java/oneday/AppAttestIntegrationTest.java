package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.attestation.AttestationGuard;
import oneday.attestation.FakeAppleAttestation;
import oneday.attestation.FakeAppleAttestation.Device;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * iOS device attestation end to end, in enforce mode: register a Secure Enclave key once, then every protected
 * request carries a single-use, action-bound assertion. Android-style tokens keep working alongside.
 */
@SpringBootTest
@TestPropertySource(properties = { "oneday.attestation.mode=enforce", "oneday.attestation.apple.enabled=true",
		"oneday.attestation.apple.app-id=" + AppAttestIntegrationTest.APP_ID,
		"oneday.attestation.apple.allow-development=true" })
class AppAttestIntegrationTest extends ApiTestSupport {

	static final String APP_ID = "ABCDE12345.app.oneday";

	private static final FakeAppleAttestation APPLE = new FakeAppleAttestation(Instant.now().truncatedTo(ChronoUnit.SECONDS));

	@DynamicPropertySource
	static void appleRoot(DynamicPropertyRegistry registry) throws IOException {
		var file = APPLE.writeRoot(Files.createTempDirectory("app-attest"));
		registry.add("oneday.attestation.apple.root-ca", () -> "file:" + file);
	}

	@Autowired
	MeterRegistry metrics;

	@Test
	void anAttestedIPhoneCanSignUpAndNothingCanBeReplayed() throws Exception {
		Device iphone = APPLE.device(APP_ID);
		String c0 = challenge();
		String attestation = Base64.getEncoder().encodeToString(iphone.attest(sha256(c0)));
		String register = "{\"keyId\":\"%s\",\"attestation\":\"%s\",\"challenge\":\"%s\"}";
		mvc.perform(post("/attestation/apple/keys").contentType(MediaType.APPLICATION_JSON)
			.content(register.formatted(iphone.keyId(), attestation, c0))).andExpect(status().isCreated());
		mvc.perform(post("/attestation/apple/keys").contentType(MediaType.APPLICATION_JSON)
			.content(register.formatted(iphone.keyId(), attestation, c0)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CHALLENGE_INVALID"));
		mvc.perform(post("/attestation/apple/keys").contentType(MediaType.APPLICATION_JSON)
			.content(register.formatted(iphone.keyId(), attestation, challenge()))).andExpect(status().isConflict());

		Device forged = new FakeAppleAttestation(Instant.now()).device(APP_ID);
		String c1 = challenge();
		mvc.perform(post("/attestation/apple/keys").contentType(MediaType.APPLICATION_JSON)
			.content(register.formatted(forged.keyId(),
					Base64.getEncoder().encodeToString(forged.attest(sha256(c1))), c1)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ATTESTATION_REJECTED"));

		signUp(null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_NOT_TRUSTED"));
		String header = iphone.header(challenge(), "signup");
		signUp(header).andExpect(status().isCreated());
		signUp(header).andExpect(status().isForbidden()); // the challenge is spent
		signUp(iphone.header(challenge(), "location")).andExpect(status().isForbidden()); // minted for another action

		// A cloned key replaying an old counter value is refused even with a fresh challenge.
		String c2 = challenge();
		byte[] stale = iphone.assertWithCounter(sha256(c2 + "|signup"), 1);
		signUp("appattest." + iphone.keyId() + "." + c2 + "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(stale)).andExpect(status().isForbidden());
		signUp(APPLE.device(APP_ID).header(challenge(), "signup")).andExpect(status().isForbidden()); // never attested

		String token = JsonPath.read(body(signUp(iphone.header(challenge(), "signup")).andExpect(status().isCreated())),
				"$.token");
		signUp("dev-ok").andExpect(status().isCreated()); // Android tokens go to their own attestor

		// A signed-in app fetches challenges under its own (per-person) budget for location updates.
		String forLocation = JsonPath.read(body(postAs(token, "/attestation/challenges", null)
			.andExpect(status().isCreated())), "$.challenge");
		perform(put("/location")
			.header(AttestationGuard.HEADER, iphone.header(forLocation, "location")), token,
				"{\"lat\":12.9352,\"lon\":77.6245}")
			.andExpect(status().isOk());

		assertThat(count("assert", "ok")).isEqualTo(3);
		assertThat(count("assert", "counter")).isEqualTo(1);
		assertThat(count("assert", "challenge")).isEqualTo(1);
		assertThat(count("assert", "unknown_key")).isEqualTo(1);
		assertThat(count("attest", "certificate_chain")).isEqualTo(1);
	}

	private String challenge() throws Exception {
		return JsonPath.read(body(mvc.perform(post("/attestation/challenges")).andExpect(status().isCreated())),
				"$.challenge");
	}

	private ResultActions signUp(String integrity) throws Exception {
		var request = post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"asha+%s@example.com","password":"%s","dateOfBirth":"1998-04-12",
				 "displayName":"Asha","consentVersion":"2026-09"}
				""".formatted(UUID.randomUUID(), PASSWORD));
		if (integrity != null) {
			request.header(AttestationGuard.HEADER, integrity);
		}
		return mvc.perform(request);
	}

	private double count(String step, String outcome) {
		return metrics.counter("oneday.app_attest", "step", step, "outcome", outcome).count();
	}

	private static byte[] sha256(String value) throws Exception {
		return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
	}
}
