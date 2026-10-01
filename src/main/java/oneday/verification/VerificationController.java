package oneday.verification;

import oneday.security.TokenService;
import oneday.verification.VerificationService.VerificationResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verification")
public class VerificationController {

	private final VerificationService verification;

	public VerificationController(VerificationService verification) {
		this.verification = verification;
	}

	/** Returns a fresh token; it carries the {@code verified} scope once the check clears. */
	@PostMapping("/liveness")
	VerificationResponse liveness(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LivenessRequest request) {
		return verification.submitLiveness(jwt.getSubject(), jwt.getClaimAsString(TokenService.SESSION_CLAIM),
				request.sessionToken());
	}

	record LivenessRequest(@NotBlank @Size(max = 512) String sessionToken) {
	}
}
