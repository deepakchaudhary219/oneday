package oneday.trust;

import oneday.trust.VouchService.MyVouches;
import oneday.trust.VouchService.VouchView;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrustController {

	private final VouchService vouches;

	public TrustController(VouchService vouches) {
		this.vouches = vouches;
	}

	@PostMapping("/connections/{connectionId}/vouch")
	VouchView vouch(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		return vouches.vouch(jwt.getSubject(), connectionId);
	}

	@DeleteMapping("/connections/{connectionId}/vouch")
	VouchView withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		return vouches.withdraw(jwt.getSubject(), connectionId);
	}

	@GetMapping("/vouches/mine")
	MyVouches mine(@AuthenticationPrincipal Jwt jwt) {
		return vouches.mine(jwt.getSubject());
	}
}
