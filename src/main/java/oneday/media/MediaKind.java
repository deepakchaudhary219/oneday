package oneday.media;

import java.util.Map;

/** Allowed upload kinds, their content types and file extensions. */
public enum MediaKind {

	PHOTO(Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/heic", "heic")),
	VIDEO(Map.of("video/mp4", "mp4", "video/quicktime", "mov"));

	private final Map<String, String> extensions;

	MediaKind(Map<String, String> extensions) {
		this.extensions = extensions;
	}

	/** File extension for an allowed content type, or {@code null} if the type is not allowed. */
	public String extensionFor(String contentType) {
		return contentType == null ? null : extensions.get(contentType.toLowerCase(java.util.Locale.ROOT));
	}
}
