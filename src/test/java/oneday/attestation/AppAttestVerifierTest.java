package oneday.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import oneday.attestation.AppAttestVerifier.Rejected;
import oneday.attestation.FakeAppleAttestation.Device;

import org.junit.jupiter.api.Test;

/** Each App Attest rule, against a stand-in Apple CA. */
class AppAttestVerifierTest {

	private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

	private static final String APP_ID = "ABCDE12345.app.oneday";

	private final FakeAppleAttestation apple = new FakeAppleAttestation(NOW);

	private final AppAttestVerifier verifier = new AppAttestVerifier(apple.root(), APP_ID, true,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final byte[] challengeHash = AppAttestVerifier.sha256("challenge".getBytes(StandardCharsets.UTF_8));

	@Test
	void aGenuineAttestationRegistersTheDeviceKey() {
		Device device = apple.device(APP_ID);
		var key = verifier.verifyAttestation(device.keyIdBytes(), device.attest(challengeHash), challengeHash);
		assertThat(key.environment()).isEqualTo("development");
		assertThat(key.publicKey()).isNotEmpty();
		var production = verifier.verifyAttestation(device.keyIdBytes(),
				device.attest(challengeHash, AppAttestVerifier.AAGUID_PRODUCTION), challengeHash);
		assertThat(production.environment()).isEqualTo("production");
	}

	@Test
	void everyAttestationRuleIsEnforced() {
		Device device = apple.device(APP_ID);
		byte[] attestation = device.attest(challengeHash);
		byte[] otherHash = AppAttestVerifier.sha256("other".getBytes(StandardCharsets.UTF_8));
		assertRejected(() -> verifier.verifyAttestation(device.keyIdBytes(), attestation, otherHash), "nonce");
		assertRejected(() -> verifier.verifyAttestation(apple.device(APP_ID).keyIdBytes(), attestation, challengeHash),
				"key id");
		Device otherApp = apple.device("ZZZZZ99999.com.copycat");
		assertRejected(() -> verifier.verifyAttestation(otherApp.keyIdBytes(), otherApp.attest(challengeHash),
				challengeHash), "app id");

		FakeAppleAttestation impostor = new FakeAppleAttestation(NOW);
		Device forged = impostor.device(APP_ID);
		assertRejected(() -> verifier.verifyAttestation(forged.keyIdBytes(), forged.attest(challengeHash), challengeHash),
				"certificate chain");

		AppAttestVerifier productionOnly = new AppAttestVerifier(apple.root(), APP_ID, false,
				Clock.fixed(NOW, ZoneOffset.UTC));
		assertRejected(() -> productionOnly.verifyAttestation(device.keyIdBytes(), attestation, challengeHash),
				"environment");
		AppAttestVerifier later = new AppAttestVerifier(apple.root(), APP_ID, true,
				Clock.fixed(NOW.plus(Duration.ofDays(400)), ZoneOffset.UTC));
		assertRejected(() -> later.verifyAttestation(device.keyIdBytes(), attestation, challengeHash),
				"certificate chain");
		assertRejected(() -> verifier.verifyAttestation(device.keyIdBytes(), new byte[] { 1, 2, 3 }, challengeHash),
				"encoding");
	}

	@Test
	void assertionsAreSignedByTheRegisteredKeyForThisRequestOnly() {
		Device device = apple.device(APP_ID);
		byte[] publicKey = verifier.verifyAttestation(device.keyIdBytes(), device.attest(challengeHash), challengeHash)
			.publicKey();
		byte[] request = AppAttestVerifier.sha256("c1|signup".getBytes(StandardCharsets.UTF_8));
		assertThat(verifier.verifyAssertion(publicKey, device.assert_(request), request)).isEqualTo(1);
		assertThat(verifier.verifyAssertion(publicKey, device.assert_(request), request)).isEqualTo(2);

		byte[] otherRequest = AppAttestVerifier.sha256("c1|location".getBytes(StandardCharsets.UTF_8));
		assertRejected(() -> verifier.verifyAssertion(publicKey, device.assert_(request), otherRequest), "signature");
		Device stranger = apple.device(APP_ID);
		assertRejected(() -> verifier.verifyAssertion(publicKey, stranger.assert_(request), request), "signature");
	}

	private static void assertRejected(Runnable check, String rule) {
		assertThatThrownBy(check::run).isInstanceOf(Rejected.class).hasMessage(rule);
	}
}
