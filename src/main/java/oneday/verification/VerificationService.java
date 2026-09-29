package oneday.verification;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.config.OneDayProperties;
import oneday.identity.User;
import oneday.identity.UserGuard;
import oneday.identity.VerificationStatus;
import oneday.security.TokenService;
import oneday.verification.LivenessVerifier.LivenessResult;
import oneday.verification.VerificationAttempt.Outcome;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liveness + age-estimate cross-check, triggered lazily at the first contact-reaching action (blueprint
 * §41.4). Only high-confidence, age-consistent results clear automatically; everything else goes to
 * manual review rather than being auto-rejected, since age estimates are noisy.
 */
@Service
public class VerificationService {

	private final ObjectProvider<LivenessVerifier> verifier;

	private final VerificationAttemptRepository attempts;

	private final UserGuard guard;

	private final TokenService tokens;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	private final OneDayProperties.Verification settings;

	public VerificationService(ObjectProvider<LivenessVerifier> verifier, VerificationAttemptRepository attempts,
			UserGuard guard, TokenService tokens, RateLimiter rateLimiter, Clock clock, OneDayProperties properties) {
		this.verifier = verifier;
		this.attempts = attempts;
		this.guard = guard;
		this.tokens = tokens;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.settings = properties.verification();
	}

	@Transactional
	public VerificationResponse submitLiveness(String userId, String sessionToken) {
		User user = guard.requireActive(userId);
		if (user.isVerified()) {
			return new VerificationResponse(user.getVerificationStatus(), "You're already verified", tokens.issue(user));
		}
		if (user.getVerificationStatus() == VerificationStatus.MANUAL_REVIEW) {
			return new VerificationResponse(user.getVerificationStatus(),
					"A person on our safety team is reviewing your check. You can keep browsing meanwhile.",
					tokens.issue(user));
		}
		LivenessVerifier provider = verifier.getIfAvailable();
		if (provider == null) {
			throw ApiException.unavailable("VERIFICATION_UNAVAILABLE", "Verification is temporarily unavailable");
		}
		if (!rateLimiter.tryAcquire("liveness:" + userId, 5, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("VERIFICATION_RATE_LIMITED", "Please wait a while before trying again");
		}

		LivenessResult result = provider.verify(sessionToken);
		Outcome outcome = decide(user, result);
		attempts.save(new VerificationAttempt(user.getId(), provider.provider(), outcome, result.estimatedAge(),
				result.confidence(), clock.instant()));

		String message;
		switch (outcome) {
			case PASSED -> {
				user.markVerified(clock.instant());
				message = "Verified. You can now post, signal and chat.";
			}
			case FAILED_LIVENESS -> message = "We couldn't confirm a live check. Try again in good light.";
			default -> {
				user.markManualReview();
				message = "Thanks! A person on our safety team will review this shortly. You can keep browsing.";
			}
		}
		return new VerificationResponse(user.getVerificationStatus(), message, tokens.issue(user));
	}

	@Transactional(readOnly = true)
	public List<VerificationAttempt> attemptsBy(String userId) {
		return attempts.findByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public void forget(String userId) {
		attempts.deleteByUserId(userId);
	}

	private Outcome decide(User user, LivenessResult result) {
		if (!result.livePerson()) {
			return Outcome.FAILED_LIVENESS;
		}
		if (result.estimatedAge() < 18) {
			return Outcome.POSSIBLE_MINOR;
		}
		int declaredAge = Period.between(user.getDateOfBirth(), LocalDate.now(clock.withZone(ZoneOffset.UTC)))
			.getYears();
		if (Math.abs(declaredAge - result.estimatedAge()) > settings.maxAgeGapYears()) {
			return Outcome.AGE_MISMATCH;
		}
		if (result.confidence() < settings.autoApproveConfidence()) {
			return Outcome.LOW_CONFIDENCE;
		}
		return Outcome.PASSED;
	}

	public record VerificationResponse(VerificationStatus status, String message, TokenService.IssuedToken token) {
	}
}
