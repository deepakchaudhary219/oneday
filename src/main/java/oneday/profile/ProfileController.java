package oneday.profile;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
public class ProfileController {

	private final ProfileService profiles;

	public ProfileController(ProfileService profiles) {
		this.profiles = profiles;
	}

	@GetMapping("/me")
	ProfileView me(@AuthenticationPrincipal Jwt jwt) {
		return profiles.me(jwt.getSubject());
	}

	@PatchMapping("/me")
	ProfileView update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		return profiles.update(jwt.getSubject(), request);
	}
}
