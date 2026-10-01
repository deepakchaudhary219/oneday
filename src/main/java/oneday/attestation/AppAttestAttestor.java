package oneday.attestation;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.attestation.AppAttestVerifier.Rejected;
import oneday.common.ApiException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apple App Attest ({@code oneday.attestation.apple.enabled=true}), the iOS counterpart of Play Integrity.
 *
 * <ol>
 * <li>Once per install: the app gets a challenge ({@code POST /attestation/challenges}), has the Secure Enclave
 * attest a new key over {@code SHA256(challenge)}, and registers it ({@code POST /attestation/apple/keys}).</li>
 * <li>Per protected request: a fresh challenge, an assertion over {@code SHA256(challenge + "|" + action)}, sent
 * as {@code X-Device-Integrity: appattest.<keyId>.<challenge>.<assertion>}.</li>
 * </ol>
 * Challenges are single-use and the key's counter only moves forward, so neither an attestation nor an
 * assertion can be replayed, and an assertion minted for one action is useless for another.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "oneday.attestation.apple.enabled", havingValue = "true")
class AppAttestAttestor implements DeviceAttestor {

	static final String PREFIX = "appattest.";

	private final AppAttestVerifier verifier;

	private final AppAttestKeyRepository keys;

	private final AttestationChallenges challenges;

	private final MeterRegistry metrics;

	private final Clock clock;

	AppAttestAttestor(AppAttestVerifier verifier, AppAttestKeyRepository keys, AttestationChallenges challenges,
			MeterRegistry metrics, Clock clock) {
		this.verifier = verifier;
		this.keys = keys;
		this.challenges = challenges;
		this.metrics = metrics;
		this.clock = clock;
	}

	@Override
	public boolean handles(String token) {
		return token.startsWith(PREFIX);
	}

	@Override
	@Transactional
	public Verdict verify(String token, String action) {
		String[] parts = token.substring(PREFIX.length()).split("\\.", -1);
		if (parts.length != 3 || !challenges.consume(parts[1])) {
			return outcome("assert", "challenge", Verdict.FAILED);
		}
		Optional<AppAttestKey> key = keys.findById(parts[0]);
		if (key.isEmpty()) {
			return outcome("assert", "unknown_key", Verdict.FAILED);
		}
		try {
			byte[] clientDataHash = AppAttestVerifier
				.sha256((parts[1] + "|" + action).getBytes(StandardCharsets.UTF_8));
			long count = verifier.verifyAssertion(key.get().getPublicKey(), decode(parts[2]), clientDataHash);
			if (keys.advance(parts[0], count, clock.instant()) != 1) {
				return outcome("assert", "counter", Verdict.FAILED);
			}
			return outcome("assert", "ok", Verdict.TRUSTED);
		}
		catch (Rejected ex) {
			return outcome("assert", ex.rule().replace(' ', '_'), Verdict.FAILED);
		}
	}

	/** Registers a Secure Enclave key after verifying Apple's attestation of it. */
	@Transactional
	public void register(String keyId, String attestation, String challenge) {
		if (!challenges.consume(challenge)) {
			throw ApiException.badRequest("CHALLENGE_INVALID", "Get a fresh challenge and try again");
		}
		if (keyId == null || keyId.length() > 64 || keys.existsById(keyId)) {
			throw ApiException.conflict("KEY_NOT_ACCEPTED", "Generate a new key and attest it");
		}
		try {
			AppAttestVerifier.RegisteredKey verified = verifier.verifyAttestation(decode(keyId), decode(attestation),
					AppAttestVerifier.sha256(challenge.getBytes(StandardCharsets.UTF_8)));
			keys.save(new AppAttestKey(keyId, verified.publicKey(), verified.environment(), clock.instant()));
			outcome("attest", "ok", Verdict.TRUSTED);
		}
		catch (Rejected ex) {
			outcome("attest", ex.rule().replace(' ', '_'), Verdict.FAILED);
			throw ApiException.unprocessable("ATTESTATION_REJECTED", "This device couldn't be verified");
		}
	}

	/** Apple hands out standard base64; URL-safe base64 (without padding) is accepted too. */
	private static byte[] decode(String value) {
		if (value == null || value.isEmpty()) {
			throw new Rejected("encoding");
		}
		try {
			String standard = value.replace('-', '+').replace('_', '/');
			return Base64.getDecoder().decode(standard + "=".repeat((4 - standard.length() % 4) % 4));
		}
		catch (IllegalArgumentException ex) {
			throw new Rejected("encoding");
		}
	}

	private Verdict outcome(String step, String result, Verdict verdict) {
		metrics.counter("oneday.app_attest", "step", step, "outcome", result).increment();
		return verdict;
	}
}
