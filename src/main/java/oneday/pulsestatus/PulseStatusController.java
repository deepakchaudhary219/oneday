package oneday.pulsestatus;

import java.util.List;

import oneday.pulsestatus.PulseStatusService.FriendStatusView;
import oneday.pulsestatus.PulseStatusService.MyStatusView;
import oneday.pulsestatus.PulseStatusService.SetStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pulse-status")
public class PulseStatusController {

	private final PulseStatusService statuses;

	public PulseStatusController(PulseStatusService statuses) {
		this.statuses = statuses;
	}

	@PutMapping
	MyStatusView set(@AuthenticationPrincipal Jwt jwt, @RequestBody SetStatus request) {
		return statuses.set(jwt.getSubject(), request);
	}

	@GetMapping("/mine")
	ResponseEntity<MyStatusView> mine(@AuthenticationPrincipal Jwt jwt) {
		return statuses.mine(jwt.getSubject()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	@DeleteMapping
	ResponseEntity<Void> clear(@AuthenticationPrincipal Jwt jwt) {
		statuses.clear(jwt.getSubject());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/friends")
	List<FriendStatusView> friends(@AuthenticationPrincipal Jwt jwt) {
		return statuses.friends(jwt.getSubject());
	}
}
