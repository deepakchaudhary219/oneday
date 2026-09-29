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
 * An upload and its processing state. The client uploads to {@code incomingKey}; the worker writes the
 * cleaned object to {@code objectKey} (the {@code mediaRef}) and deletes the original. Keys are random and
 * never contain a user id: ownership lives only in this table.
 */
@Entity
@Table(name = "media_uploads")
public class MediaUpload {

	public enum Status {
		/** Ticket issued; waiting for the client's PUT and {@code complete} call. */
		AWAITING_UPLOAD,
		/** Being re-encoded and stripped of metadata. */
		PROCESSING,
		/** Cleaned object is available; may be attached to a moment. */
		READY,
		/** Not a valid/acceptable file; never served. */
		REJECTED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String ownerId;

	@Column(nullable = false, unique = true)
	private String objectKey;

	private String incomingKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MediaKind kind;

	@Column(nullable = false)
	private String contentType;

	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	private String rejectReason;

	private int attempts;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant updatedAt;

	protected MediaUpload() {
	}

	MediaUpload(String ownerId, String objectKey, String incomingKey, MediaKind kind, String contentType,
			long sizeBytes, Instant now) {
		this.id = Ids.newId();
		this.ownerId = ownerId;
		this.objectKey = objectKey;
		this.incomingKey = incomingKey;
		this.kind = kind;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.status = Status.AWAITING_UPLOAD;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void startProcessing(Instant now) {
		this.status = Status.PROCESSING;
		this.updatedAt = now;
	}

	void markReady(Instant now) {
		this.status = Status.READY;
		this.rejectReason = null;
		this.updatedAt = now;
	}

	void reject(String reason, Instant now) {
		this.status = Status.REJECTED;
		this.rejectReason = reason;
		this.updatedAt = now;
	}

	/** A transient failure; returns the attempt count so far. */
	int recordFailedAttempt(Instant now) {
		this.attempts++;
		this.updatedAt = now;
		return attempts;
	}

	public String getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getObjectKey() {
		return objectKey;
	}

	public String getIncomingKey() {
		return incomingKey;
	}

	public MediaKind getKind() {
		return kind;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Status getStatus() {
		return status;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
