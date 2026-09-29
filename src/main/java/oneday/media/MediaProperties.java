package oneday.media;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider {@code s3} (AWS S3 or MinIO), {@code dev} (in-memory store served by the app) or {@code none}
 * @param endpoint custom endpoint (e.g. MinIO {@code http://localhost:9000}); empty for AWS
 * @param accessKey static credentials; empty to use the default AWS credential chain (IAM role)
 * @param devBaseUrl where the {@code dev} provider's signed upload/view URLs point (this app)
 * @param maxPhotoPixels decoded-size limit, the guard against decompression bombs
 * @param maxVideoSeconds longest accepted video
 * @param asyncProcessing process on a worker pool ({@code true}) or inline after commit ({@code false}, tests)
 */
@ConfigurationProperties("oneday.media")
public record MediaProperties(
		String provider,
		String bucket,
		String region,
		String endpoint,
		String accessKey,
		String secretKey,
		Duration uploadTtl,
		Duration viewTtl,
		long maxPhotoBytes,
		long maxVideoBytes,
		int uploadsPerHour,
		String devBaseUrl,
		long maxPhotoPixels,
		int maxVideoSeconds,
		boolean asyncProcessing,
		String ffmpegPath,
		String ffprobePath) {
}
