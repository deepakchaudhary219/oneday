package oneday.security;

import java.util.List;

import oneday.security.SessionService.SessionView;
import oneday.security.TokenService.IssuedToken;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Keeping a device signed in, and signing devices out. */
@RestController
@RequestMapping("/auth")
public class SessionController {

	private final SessionService sessions;

	public SessionController(SessionService sessions) {
		this.sessions = sessions;
	}

	/** Swaps the refresh token for a new pair; the old refresh token stops working. */
	@PostMapping("/refresh")
	IssuedToken refresh(@Valid @RequestBody RefreshRequest request) {
		return sessions.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
		sessions.signOut(jwt.getSubject(), jwt.getClaimAsString(TokenService.SESSION_CLAIM));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout-all")
	ResponseEntity<Void> logoutEverywhere(@AuthenticationPrincipal Jwt jwt) {
		sessions.signOutEverywhere(jwt.getSubject());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/sessions")
	List<SessionView> list(@AuthenticationPrincipal Jwt jwt) {
		return sessions.list(jwt.getSubject(), jwt.getClaimAsString(TokenService.SESSION_CLAIM));
	}

	@DeleteMapping("/sessions/{sessionId}")
	ResponseEntity<Void> signOutDevice(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
		sessions.signOutDevice(jwt.getSubject(), sessionId);
		return ResponseEntity.noContent().build();
	}

	record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
	}
}
