package oneday.figures;

import java.util.List;

import oneday.figures.PublicFigureService.Decision;
import oneday.figures.PublicFigureService.FeedItem;
import oneday.figures.PublicFigureService.FigureView;
import oneday.figures.PublicFigureService.MyFigureView;
import oneday.figures.PublicFigureService.ReviewItem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicFigureController {

	private final PublicFigureService figures;

	public PublicFigureController(PublicFigureService figures) {
		this.figures = figures;
	}

	@PostMapping("/figures/apply")
	@ResponseStatus(HttpStatus.CREATED)
	MyFigureView apply(@AuthenticationPrincipal Jwt jwt, @RequestBody Application request) {
		return figures.apply(jwt.getSubject(), request.handle(), request.publicName(), request.category(),
				request.bio(), request.evidence());
	}

	@GetMapping("/figures/me")
	ResponseEntity<MyFigureView> mine(@AuthenticationPrincipal Jwt jwt) {
		return figures.mine(jwt.getSubject()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	@GetMapping("/figures/following")
	List<FigureView> following(@AuthenticationPrincipal Jwt jwt) {
		return figures.following(jwt.getSubject());
	}

	@GetMapping("/figures/feed")
	List<FeedItem> feed(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "30") int limit) {
		return figures.feed(jwt.getSubject(), limit);
	}

	@GetMapping("/figures/{handle}")
	FigureView profile(@AuthenticationPrincipal Jwt jwt, @PathVariable String handle) {
		return figures.profile(jwt.getSubject(), handle);
	}

	@PostMapping("/figures/{handle}/follow")
	FigureView follow(@AuthenticationPrincipal Jwt jwt, @PathVariable String handle) {
		return figures.follow(jwt.getSubject(), handle);
	}

	@DeleteMapping("/figures/{handle}/follow")
	ResponseEntity<Void> unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable String handle) {
		figures.unfollow(jwt.getSubject(), handle);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/staff/figures")
	List<ReviewItem> queue(@AuthenticationPrincipal Jwt jwt) {
		return figures.queue(jwt.getSubject());
	}

	@PostMapping("/staff/figures/{userId}/decision")
	MyFigureView decide(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId,
			@RequestBody DecisionRequest request) {
		return figures.decide(jwt.getSubject(), userId, request.decision(), request.note());
	}

	/** {@code evidence}: how staff can verify you (official links, press, federation or party listing). */
	record Application(String handle, String publicName, FigureCategory category, String bio, String evidence) {
	}

	record DecisionRequest(Decision decision, String note) {
	}
}
