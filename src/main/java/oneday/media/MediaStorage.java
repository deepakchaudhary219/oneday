package oneday.media;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Object storage port. Clients upload straight to storage with a short-lived pre-signed URL, so media
 * bytes never pass through the API servers; reads also use short-lived URLs over a private bucket. The
 * processing worker is the only code that reads or writes object bytes.
 */
public interface MediaStorage {

	PresignedUpload presignUpload(String key, String contentType, long contentLength, Duration ttl);

	String presignView(String key, Duration ttl);

	/** Copies an object to a local file that must not exist yet. */
	void download(String key, Path target) throws IOException;

	void upload(String key, Path source, String contentType) throws IOException;

	/** Best-effort deletion (erasure, deleted moments). A bucket lifecycle rule is the backstop. */
	void delete(Collection<String> keys);

	/** @param headers headers the client must send unchanged with the PUT (they are part of the signature) */
	record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
	}

	/** The object does not exist (for example, the client never uploaded it). */
	class MissingObjectException extends IOException {

		public MissingObjectException(String key) {
			super("No object at " + key);
		}
	}
}
