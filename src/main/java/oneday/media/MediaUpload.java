package oneday.media;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An upload ticket. The object key is random and carries no user id, so a media URL can never be
 * linked back to an account; ownership lives only in this table.
 */
@Entity
@Table(name = "media_uploads")
public class MediaUpload {

	@Id
	private String id;

	@Column(nullable = false)
	private String ownerId;

	@Column(nullable = false, unique = true)
	private String objectKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MediaKind kind;

	@Column(nullable = false)
	private String contentType;

	private long sizeBytes;

	@Column(nullable = false)
	private Instant createdAt;

	protected MediaUpload() {
	}

	MediaUpload(String ownerId, String objectKey, MediaKind kind, String contentType, long sizeBytes, Instant now) {
		this.id = Ids.newId();
		this.ownerId = ownerId;
		this.objectKey = objectKey;
		this.kind = kind;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.createdAt = now;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getObjectKey() {
		return objectKey;
	}

	public MediaKind getKind() {
		return kind;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
