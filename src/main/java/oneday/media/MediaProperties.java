package oneday.media;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider {@code s3} (AWS S3 or MinIO), {@code dev} (fake URLs for local work/tests) or {@code none}
 * @param endpoint custom endpoint (e.g. MinIO {@code http://localhost:9000}); empty for AWS
 * @param accessKey static credentials; empty to use the default AWS credential chain (IAM role)
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
		int uploadsPerHour) {
}
