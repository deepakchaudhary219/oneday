package oneday.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import oneday.media.MediaStorage.PresignedUpload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Presigning is pure computation, so the real S3 adapter can be checked without network access. */
class S3MediaStorageTest {

	private final S3MediaStorage storage = storage("http://localhost:9000", "");

	private S3MediaStorage internal;

	@AfterEach
	void close() {
		storage.destroy();
		if (internal != null) {
			internal.destroy();
		}
	}

	@Test
	void uploadUrlsAreShortLivedAndBindContentTypeAndLength() {
		PresignedUpload upload = storage.presignUpload("moments/2026/09/abc.jpg", "image/jpeg", 250_000,
				Duration.ofMinutes(10));
		URI url = URI.create(upload.url());
		assertThat(url.getHost()).isEqualTo("localhost");
		assertThat(url.getPath()).isEqualTo("/oneday-media/moments/2026/09/abc.jpg");
		assertThat(url.getQuery()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256", "X-Amz-Expires=600",
				"X-Amz-Signature=");
		String signedHeaders = url.getQuery().replaceAll(".*X-Amz-SignedHeaders=([^&]*).*", "$1");
		assertThat(signedHeaders).contains("content-type").contains("content-length");
		assertThat(upload.headers()).containsEntry("content-type", "image/jpeg")
			.containsEntry("content-length", "250000")
			.doesNotContainKey("host");
	}

	@Test
	void viewUrlsExpireAfterTheConfiguredTtl() {
		String view = storage.presignView("moments/2026/09/abc.jpg", Duration.ofHours(1));
		assertThat(view).startsWith("http://localhost:9000/oneday-media/moments/2026/09/abc.jpg?")
			.contains("X-Amz-Expires=3600");
	}

	@Test
	void signedUrlsUseThePublicHostWhenTheStoreIsReachedInternallyUnderAnother() {
		internal = storage("http://minio:9000", "https://media.oneday.example");
		assertThat(internal.presignView("moments/2026/09/abc.jpg", Duration.ofHours(1)))
			.startsWith("https://media.oneday.example/oneday-media/moments/2026/09/abc.jpg?");
		assertThat(internal.presignUpload("moments/2026/09/abc.jpg", "image/jpeg", 1, Duration.ofMinutes(10)).url())
			.startsWith("https://media.oneday.example/oneday-media/");
	}

	private static S3MediaStorage storage(String endpoint, String publicEndpoint) {
		return new S3MediaStorage(new MediaProperties("s3", "oneday-media", "ap-south-1", endpoint, publicEndpoint,
				"test-access-key", "test-secret-key", Duration.ofMinutes(10), Duration.ofHours(1), 15_728_640,
				104_857_600, 30, "", 40_000_000, 60, false, "ffmpeg", "ffprobe"));
	}
}
