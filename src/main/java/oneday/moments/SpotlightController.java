package oneday.moments;

import java.util.List;

import oneday.moments.SpotlightService.SpotlightView;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpotlightController {

	private final SpotlightService spotlights;

	public SpotlightController(SpotlightService spotlights) {
		this.spotlights = spotlights;
	}

	@PostMapping("/moments/{momentId}/spotlights")
	@ResponseStatus(HttpStatus.CREATED)
	SpotlightView propose(@AuthenticationPrincipal Jwt jwt, @PathVariable String momentId,
			@RequestBody ProposeRequest request) {
		return spotlights.propose(jwt.getSubject(), momentId, request.answerMomentId());
	}

	@GetMapping("/spotlights/requests")
	List<SpotlightView> requests(@AuthenticationPrincipal Jwt jwt) {
		return spotlights.requests(jwt.getSubject());
	}

	@PostMapping("/spotlights/{spotlightId}/accept")
	SpotlightView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String spotlightId) {
		return spotlights.respond(jwt.getSubject(), spotlightId, true);
	}

	@PostMapping("/spotlights/{spotlightId}/decline")
	SpotlightView decline(@AuthenticationPrincipal Jwt jwt, @PathVariable String spotlightId) {
		return spotlights.respond(jwt.getSubject(), spotlightId, false);
	}

	@DeleteMapping("/spotlights/{spotlightId}")
	ResponseEntity<Void> withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable String spotlightId) {
		spotlights.withdraw(jwt.getSubject(), spotlightId);
		return ResponseEntity.noContent().build();
	}

	record ProposeRequest(String answerMomentId) {
	}
}
