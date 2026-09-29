package oneday.media;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Object storage port. Clients upload straight to storage with a short-lived pre-signed URL, so media
 * bytes never pass through the API servers; reads also use short-lived URLs over a private bucket.
 */
public interface MediaStorage {

	PresignedUpload presignUpload(String key, String contentType, long contentLength, Duration ttl);

	String presignView(String key, Duration ttl);

	/** Best-effort deletion (erasure, deleted moments). A bucket lifecycle rule is the backstop. */
	void delete(Collection<String> keys);

	/** @param headers headers the client must send unchanged with the PUT (they are part of the signature) */
	record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
	}
}
