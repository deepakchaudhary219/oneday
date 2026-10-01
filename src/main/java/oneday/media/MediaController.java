package oneday.media;

import oneday.media.MediaService.UploadStatus;
import oneday.media.MediaService.UploadTicket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/media")
public class MediaController {

	private final MediaService media;

	public MediaController(MediaService media) {
		this.media = media;
	}

	@PostMapping("/uploads")
	@ResponseStatus(HttpStatus.CREATED)
	UploadTicket createUpload(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest request) {
		return media.createUpload(jwt.getSubject(), request.kind(), request.contentType(), request.sizeBytes());
	}

	@PostMapping("/uploads/complete")
	UploadStatus complete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MediaRefRequest request) {
		return media.complete(jwt.getSubject(), request.mediaRef());
	}

	@GetMapping("/uploads/status")
	UploadStatus status(@AuthenticationPrincipal Jwt jwt, @RequestParam String mediaRef) {
		return media.status(jwt.getSubject(), mediaRef);
	}

	record MediaRefRequest(@NotBlank @Size(max = 120) String mediaRef) {
	}

	record UploadRequest(@NotNull MediaKind kind, @NotBlank @Size(max = 64) String contentType,
			@NotNull @Positive Long sizeBytes) {
	}
}
