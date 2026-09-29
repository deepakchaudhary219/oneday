package oneday.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import oneday.config.OneDayProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory object store for local development and tests, served by {@link DevMediaController}. It
 * behaves like pre-signed S3: URLs expire and carry an HMAC over method, key, expiry and (for uploads)
 * content type and length, so the real upload → process → serve path runs end to end without cloud storage.
 */
@Component
@ConditionalOnProperty(name = "oneday.media.provider", havingValue = "dev")
public class DevMediaStorage implements MediaStorage {

	private final Clock clock;

	private final String baseUrl;

	private final SecretKeySpec key;

	private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

	private final List<String> deleted = new CopyOnWriteArrayList<>();

	public DevMediaStorage(Clock clock, MediaProperties media, OneDayProperties properties) {
		this.clock = clock;
		this.baseUrl = media.devBaseUrl().endsWith("/") ? media.devBaseUrl() : media.devBaseUrl() + "/";
		this.key = new SecretKeySpec(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Override
	public PresignedUpload presignUpload(String objectKey, String contentType, long contentLength, Duration ttl) {
		long expires = clock.instant().plus(ttl).getEpochSecond();
		String signature = sign("PUT", objectKey, expires, contentType + "|" + contentLength);
		return new PresignedUpload(baseUrl + objectKey + "?expires=" + expires + "&sig=" + signature,
				Map.of("content-type", contentType, "content-length", String.valueOf(contentLength)),
				clock.instant().plus(ttl));
	}

	@Override
	public String presignView(String objectKey, Duration ttl) {
		long expires = clock.instant().plus(ttl).getEpochSecond();
		return baseUrl + objectKey + "?expires=" + expires + "&sig=" + sign("GET", objectKey, expires, "");
	}

	@Override
	public void download(String objectKey, Path target) throws IOException {
		StoredObject object = objects.get(objectKey);
		if (object == null) {
			throw new MissingObjectException(objectKey);
		}
		Files.write(target, object.bytes());
	}

	@Override
	public void upload(String objectKey, Path source, String contentType) throws IOException {
		objects.put(objectKey, new StoredObject(Files.readAllBytes(source), contentType));
	}

	@Override
	public void delete(Collection<String> keys) {
		keys.forEach(objects::remove);
		deleted.addAll(keys);
	}

	/** Accepts a client PUT if the signed URL is valid, unexpired and matches type and length. */
	boolean acceptUpload(String objectKey, long expires, String signature, String contentType, byte[] body) {
		String expected = sign("PUT", objectKey, expires, contentType + "|" + body.length);
		if (!valid(expected, signature, expires)) {
			return false;
		}
		objects.put(objectKey, new StoredObject(body, contentType));
		return true;
	}

	/** Like S3: a bad or expired signature is refused (403) before the key is even looked at. */
	boolean validView(String objectKey, long expires, String signature) {
		return valid(sign("GET", objectKey, expires, ""), signature, expires);
	}

	/** Like S3: a validly signed request for a key that isn't there (never uploaded, or deleted) is a 404. */
	Optional<StoredObject> find(String objectKey) {
		return Optional.ofNullable(objects.get(objectKey));
	}

	public boolean contains(String objectKey) {
		return objects.containsKey(objectKey);
	}

	public List<String> deletedKeys() {
		return List.copyOf(deleted);
	}

	private boolean valid(String expected, String given, long expires) {
		return given != null && clock.instant().getEpochSecond() <= expires
				&& MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
	}

	private String sign(String method, String objectKey, long expires, String extra) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			byte[] digest = mac.doFinal(("dev-media|" + method + "|" + objectKey + "|" + expires + "|" + extra)
				.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 16);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 unavailable", ex);
		}
	}

	record StoredObject(byte[] bytes, String contentType) {
	}
}
