package oneday.verification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for local development and tests. Only active when
 * {@code oneday.verification.provider=dev}; production defaults to {@code none} so a misconfigured
 * deployment can never auto-approve anyone.
 */
@Component
@ConditionalOnProperty(name = "oneday.verification.provider", havingValue = "dev")
class DevLivenessVerifier implements LivenessVerifier {

	@Override
	public String provider() {
		return "dev";
	}

	@Override
	public LivenessResult verify(String sessionToken) {
		return switch (sessionToken) {
			case "dev-pass" -> new LivenessResult(true, 26, 0.97);
			case "dev-low-confidence" -> new LivenessResult(true, 24, 0.55);
			case "dev-looks-minor" -> new LivenessResult(true, 15, 0.93);
			default -> new LivenessResult(false, 0, 0.0);
		};
	}
}
