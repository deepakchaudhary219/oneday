package oneday.media;

import java.util.Locale;
import java.util.Map;

/**
 * Allowed upload kinds. Capture happens in the app's own camera, so the app controls the format: JPEG/PNG
 * photos and MP4/MOV videos. Everything is re-encoded server-side; photos are always served as JPEG and
 * videos as MP4.
 */
public enum MediaKind {

	PHOTO(Map.of("image/jpeg", "jpg", "image/png", "png"), "jpg", "image/jpeg"),
	VIDEO(Map.of("video/mp4", "mp4", "video/quicktime", "mov"), "mp4", "video/mp4");

	private final Map<String, String> inputExtensions;

	private final String outputExtension;

	private final String outputContentType;

	MediaKind(Map<String, String> inputExtensions, String outputExtension, String outputContentType) {
		this.inputExtensions = inputExtensions;
		this.outputExtension = outputExtension;
		this.outputContentType = outputContentType;
	}

	/** File extension for an allowed upload content type, or {@code null} if the type is not allowed. */
	public String extensionFor(String contentType) {
		return contentType == null ? null : inputExtensions.get(contentType.toLowerCase(Locale.ROOT));
	}

	public String outputExtension() {
		return outputExtension;
	}

	public String outputContentType() {
		return outputContentType;
	}
}
