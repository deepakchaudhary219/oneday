package oneday.notify;

import java.util.List;

import oneday.notify.NotificationService.DeviceView;
import oneday.notify.NotificationService.NoticeView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@PostMapping("/devices")
	@ResponseStatus(HttpStatus.CREATED)
	DeviceView register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceRequest request) {
		return notifications.register(jwt.getSubject(), request.pushToken(), request.platform());
	}

	@PostMapping("/devices/unregister")
	ResponseEntity<Void> unregister(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TokenRequest request) {
		notifications.unregister(jwt.getSubject(), request.pushToken());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/notices")
	List<NoticeView> notices(@AuthenticationPrincipal Jwt jwt) {
		return notifications.notices(jwt.getSubject());
	}

	@PostMapping("/notices/{noticeId}/read")
	ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable String noticeId) {
		notifications.markRead(jwt.getSubject(), noticeId);
		return ResponseEntity.noContent().build();
	}

	record DeviceRequest(@NotBlank @Size(max = 300) String pushToken, @NotNull Device.Platform platform) {
	}

	record TokenRequest(@NotBlank @Size(max = 300) String pushToken) {
	}
}
