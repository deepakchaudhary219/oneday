package oneday.capsules;

import java.time.Instant;
import java.util.List;

import oneday.capsules.TimeCapsuleService.CapsuleView;
import oneday.media.MediaKind;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/capsules")
public class TimeCapsuleController {

	private final TimeCapsuleService capsules;

	public TimeCapsuleController(TimeCapsuleService capsules) {
		this.capsules = capsules;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CapsuleView seal(@AuthenticationPrincipal Jwt jwt, @RequestBody SealRequest request) {
		return capsules.seal(jwt.getSubject(), request.connectionId(), request.message(), request.mediaKind(),
				request.mediaRef(), request.opensAt());
	}

	@GetMapping("/sent")
	List<CapsuleView> sent(@AuthenticationPrincipal Jwt jwt) {
		return capsules.sent(jwt.getSubject());
	}

	@GetMapping("/incoming")
	List<CapsuleView> incoming(@AuthenticationPrincipal Jwt jwt) {
		return capsules.incoming(jwt.getSubject());
	}

	@GetMapping("/{capsuleId}")
	CapsuleView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String capsuleId) {
		return capsules.get(jwt.getSubject(), capsuleId);
	}

	@DeleteMapping("/{capsuleId}")
	ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String capsuleId) {
		capsules.cancel(jwt.getSubject(), capsuleId);
		return ResponseEntity.noContent().build();
	}

	/** {@code connectionId} absent: a capsule to yourself. {@code mediaRef} from a READY upload. */
	record SealRequest(String connectionId, String message, MediaKind mediaKind, String mediaRef, Instant opensAt) {
	}
}
