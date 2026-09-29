package oneday.media;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stands in for S3 when {@code oneday.media.provider=dev}: signed PUT to upload, signed GET to read. */
@RestController
@RequestMapping(DevMediaController.PREFIX)
@ConditionalOnProperty(name = "oneday.media.provider", havingValue = "dev")
class DevMediaController {

	static final String PREFIX = "/dev-media";

	private final DevMediaStorage storage;

	DevMediaController(DevMediaStorage storage) {
		this.storage = storage;
	}

	@PutMapping("/**")
	ResponseEntity<Void> upload(HttpServletRequest request, @RequestParam long expires, @RequestParam String sig,
			@RequestHeader(value = "Content-Type", required = false) String contentType,
			@RequestBody(required = false) byte[] body) {
		boolean accepted = storage.acceptUpload(objectKey(request), expires, sig, baseType(contentType),
				body == null ? new byte[0] : body);
		return ResponseEntity.status(accepted ? HttpStatus.OK : HttpStatus.FORBIDDEN).build();
	}

	@GetMapping("/**")
	ResponseEntity<byte[]> view(HttpServletRequest request, @RequestParam long expires, @RequestParam String sig) {
		String key = objectKey(request);
		if (!storage.validView(key, expires, sig)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return storage.find(key)
			.map(o -> ResponseEntity.ok().contentType(MediaType.parseMediaType(o.contentType())).body(o.bytes()))
			.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

	private static String objectKey(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length() + PREFIX.length() + 1);
	}

	private static String baseType(String contentType) {
		return contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(java.util.Locale.ROOT);
	}
}
