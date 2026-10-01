package oneday.live;

import java.util.List;

import oneday.live.LiveService.HostView;
import oneday.live.LiveService.LiveView;
import oneday.live.LiveService.ViewerAccess;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/live")
public class LiveController {

	private final LiveService live;

	public LiveController(LiveService live) {
		this.live = live;
	}

	/** {@code threadId} absent: go live to all your Connections. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	HostView start(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) StartRequest request) {
		return live.start(jwt.getSubject(), request == null ? null : request.threadId(),
				request == null ? null : request.title());
	}

	@GetMapping
	List<LiveView> available(@AuthenticationPrincipal Jwt jwt) {
		return live.available(jwt.getSubject());
	}

	@GetMapping("/{liveId}/host")
	HostView host(@AuthenticationPrincipal Jwt jwt, @PathVariable String liveId) {
		return live.hostView(jwt.getSubject(), liveId);
	}

	@PostMapping("/{liveId}/join")
	ViewerAccess join(@AuthenticationPrincipal Jwt jwt, @PathVariable String liveId) {
		return live.join(jwt.getSubject(), liveId);
	}

	@PostMapping("/{liveId}/end")
	LiveView end(@AuthenticationPrincipal Jwt jwt, @PathVariable String liveId) {
		return live.end(jwt.getSubject(), liveId);
	}

	record StartRequest(String threadId, String title) {
	}
}
