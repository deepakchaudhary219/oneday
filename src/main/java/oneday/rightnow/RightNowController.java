package oneday.rightnow;

import java.util.List;

import oneday.rightnow.RightNowService.AcceptView;
import oneday.rightnow.RightNowService.JoinView;
import oneday.rightnow.RightNowService.MySessionView;
import oneday.rightnow.RightNowService.NearbyView;
import oneday.rightnow.RightNowService.RequestView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/right-now")
public class RightNowController {

	private final RightNowService rightNow;

	public RightNowController(RightNowService rightNow) {
		this.rightNow = rightNow;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	MySessionView start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest request) {
		return rightNow.start(jwt.getSubject(), request.activity(), request.minutes() == null ? 60 : request.minutes());
	}

	@GetMapping("/mine")
	ResponseEntity<MySessionView> mine(@AuthenticationPrincipal Jwt jwt) {
		return rightNow.current(jwt.getSubject()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	@DeleteMapping
	ResponseEntity<Void> stop(@AuthenticationPrincipal Jwt jwt) {
		rightNow.stop(jwt.getSubject());
		return ResponseEntity.noContent().build();
	}

	@GetMapping
	List<NearbyView> nearby(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String activity) {
		return rightNow.nearby(jwt.getSubject(), activity);
	}

	@PostMapping("/{sessionId}/join")
	JoinView join(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
		return rightNow.join(jwt.getSubject(), sessionId);
	}

	@GetMapping("/requests")
	List<RequestView> requests(@AuthenticationPrincipal Jwt jwt) {
		return rightNow.requests(jwt.getSubject());
	}

	@PostMapping("/requests/{requestId}/accept")
	AcceptView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String requestId) {
		return rightNow.accept(jwt.getSubject(), requestId);
	}

	@PostMapping("/requests/{requestId}/decline")
	ResponseEntity<Void> decline(@AuthenticationPrincipal Jwt jwt, @PathVariable String requestId) {
		rightNow.decline(jwt.getSubject(), requestId);
		return ResponseEntity.noContent().build();
	}

	public record StartRequest(@NotBlank @Size(max = 30) String activity, Integer minutes) {
	}
}
