package oneday.identity;

import oneday.attestation.AttestationGuard;

import java.time.LocalDate;

import oneday.identity.OtpService.ChallengeView;
import oneday.identity.OtpService.PhoneAuthResult;
import oneday.identity.OtpService.SignupDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/otp")
public class PhoneAuthController {

	private final OtpService otp;

	private final AttestationGuard attestation;

	public PhoneAuthController(OtpService otp, AttestationGuard attestation) {
		this.otp = otp;
		this.attestation = attestation;
	}

	@PostMapping("/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	ChallengeView request(@Valid @RequestBody OtpRequest request, HttpServletRequest http) {
		return otp.request(request.phone(), http.getRemoteAddr());
	}

	@PostMapping("/verify")
	PhoneAuthResult verify(@Valid @RequestBody OtpVerify request, HttpServletRequest http) {
		attestation.check(http.getHeader(AttestationGuard.HEADER), request.displayName() == null ? "signin" : "signup");
		return otp.verify(request.challengeId(), request.phone(), request.code(),
				new SignupDetails(request.displayName(), request.dateOfBirth(), request.consentVersion()),
				ClientInfo.of(http));
	}

	record OtpRequest(@NotBlank @Size(max = 24) String phone) {
	}

	/** {@code displayName}, {@code dateOfBirth} and {@code consentVersion} are only needed for a new number. */
	record OtpVerify(@NotBlank String challengeId, @NotBlank @Size(max = 24) String phone,
			@NotBlank @Size(max = 8) String code, @Size(max = 40) String displayName, LocalDate dateOfBirth,
			@Size(max = 32) String consentVersion) {
	}
}
