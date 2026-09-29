package oneday.privacy;

import java.util.Map;

import oneday.common.ApiException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/privacy")
public class PrivacyController {

	private final PrivacyService privacy;

	public PrivacyController(PrivacyService privacy) {
		this.privacy = privacy;
	}

	@GetMapping("/export")
	Map<String, Object> export(@AuthenticationPrincipal Jwt jwt) {
		return privacy.export(jwt.getSubject());
	}

	/** Irreversible; requires {@code ?confirm=DELETE} so it can't be triggered by accident. */
	@DeleteMapping("/account")
	ResponseEntity<Void> erase(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String confirm) {
		if (!"DELETE".equals(confirm)) {
			throw ApiException.badRequest("CONFIRMATION_REQUIRED", "Add ?confirm=DELETE to erase your account");
		}
		privacy.erase(jwt.getSubject());
		return ResponseEntity.noContent().build();
	}
}
