package oneday.attestation;

import java.time.Duration;

import oneday.attestation.AttestationChallenges.Issued;
import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.identity.ClientInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated on purpose: signup itself is attested, so these come before any account exists. */
@RestController
@RequestMapping("/attestation")
class AttestationController {

	private final AttestationChallenges challenges;

	private final ObjectProvider<AppAttestAttestor> appAttest;

	private final RateLimiter rateLimiter;

	AttestationController(AttestationChallenges challenges, ObjectProvider<AppAttestAttestor> appAttest,
			RateLimiter rateLimiter) {
		this.challenges = challenges;
		this.appAttest = appAttest;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Every protected request needs a fresh challenge, so a signed-in app is limited per person. Only pre-signup
	 * traffic is limited per IP, generously, because carrier-grade NAT puts many phones behind one address.
	 */
	@PostMapping("/challenges")
	@ResponseStatus(HttpStatus.CREATED)
	Issued challenge(HttpServletRequest http, @AuthenticationPrincipal Jwt jwt) {
		if (jwt != null) {
			limit("attestation-challenge:user:" + jwt.getSubject(), 240);
		}
		else {
			limit("attestation-challenge:ip:" + ClientInfo.of(http).ip(), 600);
		}
		return challenges.issue();
	}

	@PostMapping("/apple/keys")
	@ResponseStatus(HttpStatus.CREATED)
	void registerAppleKey(HttpServletRequest http, @Valid @RequestBody AppleKey request) {
		AppAttestAttestor attestor = appAttest.getIfAvailable();
		if (attestor == null) {
			throw ApiException.notFound("App Attest");
		}
		limit("attestation-key:" + ClientInfo.of(http).ip(), 20);
		attestor.register(request.keyId(), request.attestation(), request.challenge());
	}

	private void limit(String key, int perHour) {
		if (!rateLimiter.tryAcquire(key, perHour, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("ATTESTATION_RATE_LIMIT", "Too many attempts. Try again later.");
		}
	}

	/** {@code attestation}: the attestation object from {@code DCAppAttestService.attestKey}, base64. */
	record AppleKey(@NotBlank @Size(max = 64) String keyId, @NotBlank @Size(max = 16384) String attestation,
			@NotBlank @Size(max = 64) String challenge) {
	}
}
