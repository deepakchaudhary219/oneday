package oneday.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import oneday.common.ApiException;
import oneday.common.Ids;
import oneday.common.RateLimiter;
import oneday.identity.UserGuard;
import oneday.media.MediaStorage.PresignedUpload;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Upload tickets, processing hand-off and short-lived view URLs.
 *
 * <p>
 * Flow: {@code POST /media/uploads} (ticket) → client {@code PUT}s to the {@code incoming/} URL →
 * {@code POST /media/uploads/complete} → the worker strips metadata and writes the served object →
 * status {@code READY} → the media can be attached to a moment. The original upload is never served, and
 * a moment may only reference its author's own, recent, processed upload.
 */
@Service
public class MediaService {

	/** How long an uploaded object stays attachable to a new moment. */
	static final Duration ATTACH_WINDOW = Duration.ofHours(24);

	/** A PROCESSING upload untouched this long is assumed lost (worker crash) and resubmitted. */
	static final Duration STALE_PROCESSING = Duration.ofMinutes(10);

	private final MediaUploadRepository uploads;

	private final ObjectProvider<MediaStorage> storage;

	private final MediaProcessor processor;

	private final UserGuard guard;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	private final MediaProperties properties;

	public MediaService(MediaUploadRepository uploads, ObjectProvider<MediaStorage> storage, MediaProcessor processor,
			UserGuard guard, RateLimiter rateLimiter, Clock clock, MediaProperties properties) {
		this.uploads = uploads;
		this.storage = storage;
		this.processor = processor;
		this.guard = guard;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.properties = properties;
	}

	@Transactional
	public UploadTicket createUpload(String userId, MediaKind kind, String contentType, long sizeBytes) {
		guard.requireContactAllowed(userId);
		MediaStorage store = requireStorage();
		if (kind == MediaKind.VIDEO && !processor.videoSupported()) {
			throw ApiException.unavailable("VIDEO_UNAVAILABLE", "Video uploads are temporarily unavailable");
		}
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
		String id = Ids.newId();
		String folder = "%d/%02d/%s".formatted(month.getYear(), month.getMonthValue(), id);
		String objectKey = "moments/" + folder + "." + kind.outputExtension();
		String incomingKey = "incoming/" + folder + "." + extension;
		String type = contentType.toLowerCase(Locale.ROOT);
		uploads.save(new MediaUpload(userId, objectKey, incomingKey, kind, type, sizeBytes, now));
		PresignedUpload presigned = store.presignUpload(incomingKey, type, sizeBytes, properties.uploadTtl());
		return new UploadTicket(objectKey, presigned.url(), "PUT", presigned.headers(), presigned.expiresAt());
	}

	/** Called after the client's PUT succeeded; processing starts once this transaction commits. */
	@Transactional
	public UploadStatus complete(String userId, String mediaRef) {
		MediaUpload upload = requireOwn(userId, mediaRef);
		if (upload.getStatus() == MediaUpload.Status.AWAITING_UPLOAD) {
			upload.startProcessing(clock.instant());
			String id = upload.getId();
			runAfterCommit(() -> processor.submit(id));
		}
		return UploadStatus.of(upload);
	}

	@Transactional(readOnly = true)
	public UploadStatus status(String userId, String mediaRef) {
		return UploadStatus.of(requireOwn(userId, mediaRef));
	}

	/** Validates that {@code mediaRef} is the caller's own, recent, processed upload of the right kind. */
	@Transactional(readOnly = true)
	public void requireAttachable(String userId, String mediaRef, MediaKind kind) {
		MediaUpload upload = uploads.findByObjectKeyAndOwnerId(mediaRef, userId)
			.filter(u -> u.getKind() == kind)
			.filter(u -> u.getCreatedAt().isAfter(clock.instant().minus(ATTACH_WINDOW)))
			.orElseThrow(() -> ApiException.unprocessable("MEDIA_NOT_FOUND",
					"Upload the " + kind.name().toLowerCase(Locale.ROOT) + " first, then attach it"));
		switch (upload.getStatus()) {
			case READY -> {
			}
			case REJECTED -> throw ApiException.unprocessable("MEDIA_REJECTED", upload.getRejectReason());
			default -> throw ApiException.conflict("MEDIA_NOT_READY", "Still preparing your media, try again shortly");
		}
	}

	/** Short-lived URL for reading an object, or {@code null} when no storage is configured. */
	public String viewUrl(String key) {
		MediaStorage store = storage.getIfAvailable();
		return key == null || store == null ? null : store.presignView(key, properties.viewTtl());
	}

	/** The silent low-fi Layer-0 rendition written next to the video by the worker. */
	public String previewUrl(String key) {
		return key == null ? null : viewUrl(previewKey(key));
	}

	/** Deletes the objects (served, preview and any original) after the surrounding transaction commits. */
	@Transactional
	public void discard(String key) {
		if (key != null) {
			List<String> keys = new ArrayList<>(List.of(key, previewKey(key)));
			uploads.findByObjectKey(key).ifPresent(u -> {
				if (u.getIncomingKey() != null) {
					keys.add(u.getIncomingKey());
				}
				uploads.delete(u);
			});
			deleteAfterCommit(keys);
		}
	}

	/** Deletes one object (and its preview) after commit, with no upload record (Memory Trail copies). */
	@Transactional
	public void discardObject(String key) {
		if (key != null) {
			deleteAfterCommit(new ArrayList<>(List.of(key, previewKey(key))));
		}
	}

	/**
	 * Copies a served object into the {@code trail/} prefix (outside the bucket rule that expires
	 * {@code moments/}) and returns the new key. Runs in a background consumer; I/O never blocks a request.
	 */
	public String copyToTrail(String key, boolean video) {
		return copyToDurable("trail", key, video);
	}

	/**
	 * Copies a served object under {@code prefix/} (no expiry rule) and returns the new key: for media that
	 * must outlive the ~3-day {@code moments/} rule (Memory Trail, Time Capsules, Collaborative Threads).
	 * Background consumers only.
	 */
	public String copyToDurable(String prefix, String key, boolean video) {
		MediaStorage store = storage.getIfAvailable();
		if (store == null) {
			throw new IllegalStateException("No media storage configured");
		}
		String target = prefix + "/" + Ids.newId();
		try {
			Path temp = Files.createTempFile("oneday-trail-", video ? ".mp4" : ".jpg");
			Files.delete(temp);
			try {
				store.download(key, temp);
				store.upload(target, temp, video ? "video/mp4" : "image/jpeg");
			}
			finally {
				Files.deleteIfExists(temp);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not copy media to the trail", ex);
		}
		return target;
	}

	/** Erasure: every upload the user ever made, records and objects. */
	@Transactional
	public void deleteAllFor(String userId) {
		List<MediaUpload> mine = uploads.findByOwnerId(userId);
		List<String> keys = new ArrayList<>();
		mine.forEach(u -> {
			keys.add(u.getObjectKey());
			keys.add(previewKey(u.getObjectKey()));
			if (u.getIncomingKey() != null) {
				keys.add(u.getIncomingKey());
			}
		});
		uploads.deleteAll(mine);
		deleteAfterCommit(keys);
	}

	/**
	 * Resubmits processing lost to a worker crash, and removes tickets whose upload never arrived (their
	 * objects, if any, are deleted too).
	 */
	@Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
	public void sweep() {
		Instant now = clock.instant();
		uploads.findByStatusAndUpdatedAtBefore(MediaUpload.Status.PROCESSING, now.minus(STALE_PROCESSING))
			.forEach(u -> processor.submit(u.getId()));
		List<MediaUpload> abandoned = uploads.findByStatusAndUpdatedAtBefore(MediaUpload.Status.AWAITING_UPLOAD,
				now.minus(ATTACH_WINDOW));
		if (!abandoned.isEmpty()) {
			MediaStorage store = storage.getIfAvailable();
			if (store != null) {
				store.delete(abandoned.stream().map(MediaUpload::getIncomingKey).toList());
			}
			uploads.deleteAll(abandoned);
		}
	}

	static String previewKey(String key) {
		return key + ".preview.mp4";
	}

	private MediaUpload requireOwn(String userId, String mediaRef) {
		return uploads.findByObjectKeyAndOwnerId(mediaRef, userId)
			.orElseThrow(() -> ApiException.notFound("Upload"));
	}

	private void deleteAfterCommit(List<String> keys) {
		MediaStorage store = storage.getIfAvailable();
		if (store != null && !keys.isEmpty()) {
			runAfterCommit(() -> store.delete(keys));
		}
	}

	private static void runAfterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		}
		else {
			action.run();
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
	 * Upload straight to storage: {@code PUT uploadUrl} with exactly these headers and the declared size,
	 * then call {@code POST /media/uploads/complete} with {@code mediaRef}.
	 */
	public record UploadTicket(String mediaRef, String uploadUrl, String method, Map<String, String> headers,
			Instant expiresAt) {
	}

	/** {@code rejectReason} is set only for {@code REJECTED}, in words the uploader can act on. */
	public record UploadStatus(String mediaRef, MediaUpload.Status status, String rejectReason) {

		static UploadStatus of(MediaUpload upload) {
			return new UploadStatus(upload.getObjectKey(), upload.getStatus(), upload.getRejectReason());
		}
	}
}
