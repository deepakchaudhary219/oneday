package oneday.ama;

import java.time.Instant;
import java.util.List;

import oneday.ama.AmaService.AmaDetail;
import oneday.ama.AmaService.AmaView;
import oneday.ama.AmaService.QuestionView;

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
@RequestMapping("/amas")
public class AmaController {

	private final AmaService amas;

	public AmaController(AmaService amas) {
		this.amas = amas;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AmaView schedule(@AuthenticationPrincipal Jwt jwt, @RequestBody ScheduleRequest request) {
		return amas.schedule(jwt.getSubject(), request.title(), request.corridorRegion(), request.startsAt(),
				request.minutes() == null ? 60 : request.minutes());
	}

	@GetMapping
	List<AmaView> list(@AuthenticationPrincipal Jwt jwt) {
		return amas.list(jwt.getSubject());
	}

	@GetMapping("/{amaId}")
	AmaDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable String amaId) {
		return amas.get(jwt.getSubject(), amaId);
	}

	@DeleteMapping("/{amaId}")
	ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String amaId) {
		amas.cancel(jwt.getSubject(), amaId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{amaId}/questions")
	@ResponseStatus(HttpStatus.CREATED)
	QuestionView ask(@AuthenticationPrincipal Jwt jwt, @PathVariable String amaId, @RequestBody AskRequest request) {
		return amas.ask(jwt.getSubject(), amaId, request.question(), Boolean.TRUE.equals(request.anonymous()),
				Boolean.TRUE.equals(request.sendAnyway()));
	}

	@PostMapping("/{amaId}/questions/{questionId}/vote")
	ResponseEntity<Void> vote(@AuthenticationPrincipal Jwt jwt, @PathVariable String amaId,
			@PathVariable String questionId) {
		amas.vote(jwt.getSubject(), amaId, questionId, true);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{amaId}/questions/{questionId}/vote")
	ResponseEntity<Void> unvote(@AuthenticationPrincipal Jwt jwt, @PathVariable String amaId,
			@PathVariable String questionId) {
		amas.vote(jwt.getSubject(), amaId, questionId, false);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{amaId}/questions/{questionId}/answer")
	QuestionView answer(@AuthenticationPrincipal Jwt jwt, @PathVariable String amaId, @PathVariable String questionId,
			@RequestBody AnswerRequest request) {
		return amas.answer(jwt.getSubject(), amaId, questionId, request.answer());
	}

	@PostMapping("/{amaId}/questions/{questionId}/hide")
	ResponseEntity<Void> hide(@AuthenticationPrincipal Jwt jwt, @PathVariable String amaId,
			@PathVariable String questionId) {
		amas.hide(jwt.getSubject(), amaId, questionId);
		return ResponseEntity.noContent().build();
	}

	/** {@code corridorRegion}: a home region such as IN-KL, or absent for everyone. {@code minutes}: 15-120. */
	record ScheduleRequest(String title, String corridorRegion, Instant startsAt, Integer minutes) {
	}

	record AskRequest(String question, Boolean anonymous, Boolean sendAnyway) {
	}

	record AnswerRequest(String answer) {
	}
}
