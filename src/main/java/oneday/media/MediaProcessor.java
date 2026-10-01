package oneday.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import oneday.common.ProductMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The media worker: download the original, clean it, upload the result, delete the original. Nothing is
 * ever served from the original upload. Bad files are rejected with a reason; transient failures are
 * retried by the sweep in {@link MediaService} and rejected after {@value #MAX_ATTEMPTS} attempts.
 */
@Component
class MediaProcessor implements DisposableBean {

	static final int MAX_ATTEMPTS = 3;

	private static final Logger log = LoggerFactory.getLogger(MediaProcessor.class);

	private final MediaUploadRepository uploads;

	private final ObjectProvider<MediaStorage> storage;

	private final PhotoProcessor photos;

	private final VideoTranscoder videos;

	private final TransactionTemplate tx;

	private final Clock clock;

	private final MediaProperties properties;

	private final ExecutorService pool;

	private final ProductMetrics metrics;

	MediaProcessor(MediaUploadRepository uploads, ObjectProvider<MediaStorage> storage, PhotoProcessor photos,
			VideoTranscoder videos, PlatformTransactionManager transactions, Clock clock, MediaProperties properties,
			ProductMetrics metrics) {
		this.uploads = uploads;
		this.storage = storage;
		this.photos = photos;
		this.videos = videos;
		// Always a fresh transaction: the worker may run inside an afterCommit callback, where joining the
		// finished request transaction would silently drop these writes.
		this.tx = new TransactionTemplate(transactions);
		this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
		this.properties = properties;
		this.metrics = metrics;
		AtomicInteger n = new AtomicInteger();
		this.pool = properties.asyncProcessing()
				? Executors.newFixedThreadPool(2, r -> new Thread(r, "media-worker-" + n.incrementAndGet())) : null;
	}

	boolean videoSupported() {
		return videos.isAvailable();
	}

	/** Runs on the worker pool, or inline when async processing is off (tests). */
	void submit(String uploadId) {
		if (pool == null) {
			process(uploadId);
		}
		else {
			pool.execute(() -> process(uploadId));
		}
	}

	void process(String uploadId) {
		MediaUpload upload = uploads.findById(uploadId).orElse(null);
		MediaStorage store = storage.getIfAvailable();
		if (upload == null || store == null || upload.getStatus() != MediaUpload.Status.PROCESSING) {
			return;
		}
		Path dir = null;
		try {
			dir = Files.createTempDirectory("oneday-media-");
			Path original = dir.resolve("original");
			store.download(upload.getIncomingKey(), original);
			if (Files.size(original) > upload.getSizeBytes()) {
				throw new MediaRejectedException("The file is larger than declared");
			}
			List<String> written = new ArrayList<>();
			if (upload.getKind() == MediaKind.PHOTO) {
				Path clean = dir.resolve("clean.jpg");
				photos.process(original, clean, properties.maxPhotoPixels());
				store.upload(upload.getObjectKey(), clean, MediaKind.PHOTO.outputContentType());
				written.add(upload.getObjectKey());
			}
			else {
				if (videos.durationSeconds(original) > properties.maxVideoSeconds()) {
					throw new MediaRejectedException(
							"Videos can be at most " + properties.maxVideoSeconds() + " seconds long");
				}
				Path clean = dir.resolve("clean.mp4");
				Path preview = dir.resolve("preview.mp4");
				videos.transcode(original, clean);
				videos.preview(original, preview);
				store.upload(upload.getObjectKey(), clean, MediaKind.VIDEO.outputContentType());
				store.upload(MediaService.previewKey(upload.getObjectKey()), preview, MediaKind.VIDEO.outputContentType());
				written.add(upload.getObjectKey());
			}
			finish(uploadId, u -> u.markReady(clock.instant()));
			metrics.mediaProcessed("ready");
			store.delete(List.of(upload.getIncomingKey()));
			log.debug("Media {} processed ({} object(s))", uploadId, written.size());
		}
		catch (MediaStorage.MissingObjectException ex) {
			finish(uploadId, u -> u.reject("We didn't receive the file. Please upload it again.", clock.instant()));
			metrics.mediaProcessed("rejected");
		}
		catch (MediaRejectedException ex) {
			finish(uploadId, u -> u.reject(ex.getMessage(), clock.instant()));
			metrics.mediaProcessed("rejected");
			store.delete(List.of(upload.getIncomingKey()));
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Media {} processing failed; will retry", uploadId, ex);
			metrics.mediaProcessed("failed");
			finish(uploadId, u -> {
				if (u.recordFailedAttempt(clock.instant()) >= MAX_ATTEMPTS) {
					u.reject("We couldn't process that file. Please try again.", clock.instant());
				}
			});
		}
		finally {
			deleteQuietly(dir);
		}
	}

	private void finish(String uploadId, java.util.function.Consumer<MediaUpload> change) {
		tx.executeWithoutResult(status -> uploads.findById(uploadId)
			.filter(u -> u.getStatus() == MediaUpload.Status.PROCESSING)
			.ifPresent(change));
	}

	private static void deleteQuietly(Path dir) {
		if (dir == null) {
			return;
		}
		try (Stream<Path> files = Files.walk(dir)) {
			files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
		catch (IOException ignored) {
			// temp files are cleaned by the OS eventually
		}
	}

	@Override
	public void destroy() {
		if (pool != null) {
			pool.shutdown();
		}
	}
}
