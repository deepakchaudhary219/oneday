package oneday.media;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import oneday.common.ApiException;
import oneday.common.Ids;
import oneday.common.RateLimiter;
import oneday.identity.UserGuard;
import oneday.media.MediaStorage.PresignedUpload;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Upload tickets and short-lived view URLs. A moment may only reference media its author uploaded
 * recently; anything else is refused, so nobody can attach someone else's photo.
 */
@Service
public class MediaService {

	/** How long an uploaded object stays attachable to a new moment. */
	static final Duration ATTACH_WINDOW = Duration.ofHours(24);

	private final MediaUploadRepository uploads;

	private final ObjectProvider<MediaStorage> storage;

	private final UserGuard guard;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	private final MediaProperties properties;

	public MediaService(MediaUploadRepository uploads, ObjectProvider<MediaStorage> storage, UserGuard guard,
			RateLimiter rateLimiter, Clock clock, MediaProperties properties) {
		this.uploads = uploads;
		this.storage = storage;
		this.guard = guard;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.properties = properties;
	}

	@Transactional
	public UploadTicket createUpload(String userId, MediaKind kind, String contentType, long sizeBytes) {
		guard.requireContactAllowed(userId);
		MediaStorage store = requireStorage();
		String extension = kind.extensionFor(contentType);
		if (extension == null) {
			throw ApiException.unprocessable("UNSUPPORTED_MEDIA_TYPE", "That file type isn't supported for " + kind);
		}
		long max = kind == MediaKind.PHOTO ? properties.maxPhotoBytes() : properties.maxVideoBytes();
		if (sizeBytes <= 0 || sizeBytes > max) {
			throw ApiException.unprocessable("MEDIA_TOO_LARGE", "Files must be at most " + (max / (1024 * 1024)) + " MB");
		}
		if (!rateLimiter.tryAcquire("upload:" + userId, properties.uploadsPerHour(), Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("UPLOAD_RATE_LIMITED", "Too many uploads this hour");
		}
		Instant now = clock.instant();
		YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
		String key = "moments/%d/%02d/%s.%s".formatted(month.getYear(), month.getMonthValue(), Ids.newId(), extension);
		uploads.save(new MediaUpload(userId, key, kind, contentType.toLowerCase(java.util.Locale.ROOT), sizeBytes, now));
		PresignedUpload presigned = store.presignUpload(key, contentType, sizeBytes, properties.uploadTtl());
		return new UploadTicket(key, presigned.url(), "PUT", presigned.headers(), presigned.expiresAt());
	}

	/** Validates that {@code mediaRef} is the caller's own, recent upload of the right kind. */
	@Transactional(readOnly = true)
	public void requireAttachable(String userId, String mediaRef, MediaKind kind) {
		uploads.findByObjectKeyAndOwnerId(mediaRef, userId)
			.filter(u -> u.getKind() == kind)
			.filter(u -> u.getCreatedAt().isAfter(clock.instant().minus(ATTACH_WINDOW)))
			.orElseThrow(() -> ApiException.unprocessable("MEDIA_NOT_FOUND",
					"Upload the " + kind.name().toLowerCase(java.util.Locale.ROOT) + " first, then attach it"));
	}

	/** Short-lived URL for reading an object, or {@code null} when no storage is configured. */
	public String viewUrl(String key) {
		MediaStorage store = storage.getIfAvailable();
		return key == null || store == null ? null : store.presignView(key, properties.viewTtl());
	}

	/** The silent low-fi Layer-0 rendition written next to the original by the transcoding worker. */
	public String previewUrl(String key) {
		return key == null ? null : viewUrl(previewKey(key));
	}

	/** Deletes the objects (original and preview) after the surrounding transaction commits. */
	@Transactional
	public void discard(String key) {
		if (key != null) {
			uploads.findByObjectKey(key).ifPresent(uploads::delete);
			afterCommit(List.of(key, previewKey(key)));
		}
	}

	/** Erasure: every upload the user ever made, records and objects. */
	@Transactional
	public void deleteAllFor(String userId) {
		List<MediaUpload> mine = uploads.findByOwnerId(userId);
		List<String> keys = new ArrayList<>();
		mine.forEach(u -> {
			keys.add(u.getObjectKey());
			keys.add(previewKey(u.getObjectKey()));
		});
		uploads.deleteAll(mine);
		afterCommit(keys);
	}

	static String previewKey(String key) {
		return key + ".preview.mp4";
	}

	private void afterCommit(List<String> keys) {
		MediaStorage store = storage.getIfAvailable();
		if (store == null || keys.isEmpty()) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					store.delete(keys);
				}
			});
		}
		else {
			store.delete(keys);
		}
	}

	private MediaStorage requireStorage() {
		MediaStorage store = storage.getIfAvailable();
		if (store == null) {
			throw ApiException.unavailable("MEDIA_UNAVAILABLE", "Media uploads are temporarily unavailable");
		}
		return store;
	}

	/**
	 * Upload straight to storage: {@code PUT uploadUrl} with exactly these headers and the declared size.
	 * Then pass {@code mediaRef} when publishing the moment.
	 */
	public record UploadTicket(String mediaRef, String uploadUrl, String method, Map<String, String> headers,
			Instant expiresAt) {
	}
}
