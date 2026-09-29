package oneday.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import oneday.media.MediaStorage.PresignedUpload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Presigning is pure computation, so the real S3 adapter can be checked without network access. */
class S3MediaStorageTest {

	private final S3MediaStorage storage = new S3MediaStorage(new MediaProperties("s3", "oneday-media", "ap-south-1",
			"http://localhost:9000", "test-access-key", "test-secret-key", Duration.ofMinutes(10), Duration.ofHours(1),
			15_728_640, 104_857_600, 30));

	@AfterEach
	void close() {
		storage.destroy();
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
}
