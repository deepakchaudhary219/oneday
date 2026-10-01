package oneday.identity;

import oneday.attestation.AttestationGuard;

import oneday.security.TokenService.IssuedToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService auth;

	private final AttestationGuard attestation;

	public AuthController(AuthService auth, AttestationGuard attestation) {
		this.auth = auth;
		this.attestation = attestation;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	IssuedToken register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		attestation.check(http.getHeader(AttestationGuard.HEADER), "signup");
		return auth.register(request, ClientInfo.of(http));
	}

	@PostMapping("/login")
	IssuedToken login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return auth.login(request, ClientInfo.of(http));
	}
}
