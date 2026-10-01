package oneday.threads;

import java.time.Instant;
import java.util.List;

import oneday.threads.ThreadService.PostView;
import oneday.threads.ThreadService.ThreadView;

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
@RequestMapping("/threads")
public class ThreadController {

	private final ThreadService threads;

	public ThreadController(ThreadService threads) {
		this.threads = threads;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ThreadView create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRequest request) {
		return threads.create(jwt.getSubject(), request.title(), request.days() == null ? 3 : request.days(),
				request.inviteConnectionIds());
	}

	@GetMapping
	List<ThreadView> mine(@AuthenticationPrincipal Jwt jwt) {
		return threads.mine(jwt.getSubject());
	}

	@GetMapping("/{threadId}")
	ThreadView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId) {
		return threads.get(jwt.getSubject(), threadId);
	}

	@PostMapping("/{threadId}/invite")
	ThreadView invite(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId,
			@RequestBody InviteRequest request) {
		return threads.invite(jwt.getSubject(), threadId, request.connectionIds());
	}

	@PostMapping("/{threadId}/accept")
	ThreadView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId) {
		return threads.respond(jwt.getSubject(), threadId, true);
	}

	@PostMapping("/{threadId}/decline")
	ThreadView decline(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId) {
		return threads.respond(jwt.getSubject(), threadId, false);
	}

	@PostMapping("/{threadId}/leave")
	ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId) {
		threads.leave(jwt.getSubject(), threadId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{threadId}/members/{handle}")
	ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId,
			@PathVariable String handle) {
		threads.remove(jwt.getSubject(), threadId, handle);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{threadId}/posts")
	@ResponseStatus(HttpStatus.CREATED)
	PostView post(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId, @RequestBody PostRequest request) {
		return threads.post(jwt.getSubject(), threadId, request.kind(), request.caption(), request.mediaRef(),
				Boolean.TRUE.equals(request.sendAnyway()));
	}

	@GetMapping("/{threadId}/posts")
	List<PostView> posts(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId,
			@RequestParam(required = false) Instant before, @RequestParam(defaultValue = "30") int limit) {
		return threads.posts(jwt.getSubject(), threadId, before, limit);
	}

	@DeleteMapping("/{threadId}/posts/{postId}")
	ResponseEntity<Void> deletePost(@AuthenticationPrincipal Jwt jwt, @PathVariable String threadId,
			@PathVariable String postId) {
		threads.deletePost(jwt.getSubject(), threadId, postId);
		return ResponseEntity.noContent().build();
	}

	/** {@code days}: 1-7 (default 3). */
	record CreateRequest(String title, Integer days, List<String> inviteConnectionIds) {
	}

	record InviteRequest(List<String> connectionIds) {
	}

	record PostRequest(ThreadPost.Kind kind, String caption, String mediaRef, Boolean sendAnyway) {
	}
}
