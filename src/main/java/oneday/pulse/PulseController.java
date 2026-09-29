package oneday.pulse;

import oneday.pulse.PulseService.PulseView;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PulseController {

	private final PulseService pulse;

	public PulseController(PulseService pulse) {
		this.pulse = pulse;
	}

	@GetMapping("/pulse")
	PulseView pulse(@AuthenticationPrincipal Jwt jwt) {
		return pulse.pulse(jwt.getSubject());
	}
}
