package oneday.moments;

import java.util.List;

import jakarta.validation.Valid;

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
@RequestMapping("/moments")
public class MomentController {

	private final MomentService moments;

	public MomentController(MomentService moments) {
		this.moments = moments;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	MomentView publish(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateMomentRequest request) {
		return moments.publish(jwt.getSubject(), request);
	}

	@GetMapping("/mine")
	List<MomentView> mine(@AuthenticationPrincipal Jwt jwt) {
		return moments.mine(jwt.getSubject());
	}

	@GetMapping("/{momentId}")
	MomentView view(@AuthenticationPrincipal Jwt jwt, @PathVariable String momentId) {
		return moments.view(jwt.getSubject(), momentId);
	}

	/** The Story Relay this moment belongs to (or started). */
	@GetMapping("/{momentId}/relay")
	MomentService.RelayView relay(@AuthenticationPrincipal Jwt jwt, @PathVariable String momentId) {
		return moments.relay(jwt.getSubject(), momentId);
	}

	@DeleteMapping("/{momentId}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String momentId) {
		moments.delete(jwt.getSubject(), momentId);
		return ResponseEntity.noContent().build();
	}
}
