package oneday.media;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * AWS S3 (use an Indian region such as ap-south-1 for data residency) or any S3-compatible store such
 * as MinIO via {@code oneday.media.endpoint}. The content type and length are part of the upload
 * signature, so a client cannot upload a different kind or a larger file than it declared.
 */
@Component
@ConditionalOnProperty(name = "oneday.media.provider", havingValue = "s3")
public class S3MediaStorage implements MediaStorage, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(S3MediaStorage.class);

	private final String bucket;

	private final S3Presigner presigner;

	private final S3Client client;

	public S3MediaStorage(MediaProperties properties) {
		this.bucket = properties.bucket();
		Region region = Region.of(properties.region());
		AwsCredentialsProvider credentials = hasText(properties.accessKey())
				? StaticCredentialsProvider
					.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()))
				: DefaultCredentialsProvider.builder().build();
		S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

		S3Presigner.Builder presignerBuilder = S3Presigner.builder()
			.region(region)
			.credentialsProvider(credentials)
			.serviceConfiguration(pathStyle);
		software.amazon.awssdk.services.s3.S3ClientBuilder clientBuilder = S3Client.builder()
			.region(region)
			.credentialsProvider(credentials)
			.serviceConfiguration(pathStyle)
			.httpClient(UrlConnectionHttpClient.create());
		if (hasText(properties.endpoint())) {
			presignerBuilder.endpointOverride(URI.create(properties.endpoint()));
			clientBuilder.endpointOverride(URI.create(properties.endpoint()));
		}
		this.presigner = presignerBuilder.build();
		this.client = clientBuilder.build();
	}

	@Override
	public PresignedUpload presignUpload(String key, String contentType, long contentLength, Duration ttl) {
		PutObjectRequest put = PutObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.contentType(contentType)
			.contentLength(contentLength)
			.build();
		PresignedPutObjectRequest presigned = presigner.presignPutObject(
				PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build());
		Map<String, String> headers = new LinkedHashMap<>();
		presigned.signedHeaders().forEach((name, values) -> {
			if (!"host".equalsIgnoreCase(name)) {
				headers.put(name, String.join(",", values));
			}
		});
		return new PresignedUpload(presigned.url().toString(), headers, presigned.expiration());
	}

	@Override
	public String presignView(String key, Duration ttl) {
		GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
		return presigner
			.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build())
			.url()
			.toString();
	}

	@Override
	public void download(String key, Path target) throws IOException {
		try {
			client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), ResponseTransformer.toFile(target));
		}
		catch (NoSuchKeyException ex) {
			throw new MissingObjectException(key);
		}
		catch (SdkException ex) {
			throw new IOException("Download failed for " + key, ex);
		}
	}

	@Override
	public void upload(String key, Path source, String contentType) throws IOException {
		try {
			client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
					RequestBody.fromFile(source));
		}
		catch (SdkException ex) {
			throw new IOException("Upload failed for " + key, ex);
		}
	}

	@Override
	public void delete(Collection<String> keys) {
		if (keys.isEmpty()) {
			return;
		}
		List<ObjectIdentifier> objects = keys.stream().map(k -> ObjectIdentifier.builder().key(k).build()).toList();
		try {
			client.deleteObjects(DeleteObjectsRequest.builder()
				.bucket(bucket)
				.delete(Delete.builder().objects(objects).quiet(true).build())
				.build());
		}
		catch (RuntimeException ex) {
			// Never fail an erasure on a storage hiccup; the bucket lifecycle rule removes leftovers.
			log.warn("Media deletion failed for {} object(s); lifecycle rule will expire them", keys.size(), ex);
		}
	}

	@Override
	public void destroy() {
		presigner.close();
		client.close();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
