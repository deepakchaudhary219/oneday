package oneday.capsules;

import java.time.Instant;

import oneday.common.Ids;
import oneday.media.MediaKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "time_capsules")
class TimeCapsule {

	enum Status {
		SEALED, OPENED, CANCELLED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String senderId;

	@Column(nullable = false)
	private String recipientId;

	/** Null for a capsule to yourself. */
	private String connectionId;

	@Column(nullable = false)
	private String message;

	@Enumerated(EnumType.STRING)
	private MediaKind mediaKind;

	private String mediaRef;

	/** The copy outside the expiring story prefix, made off-request after sealing. */
	private String durableMediaRef;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant opensAt;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant openedAt;

	protected TimeCapsule() {
	}

	TimeCapsule(String senderId, String recipientId, String connectionId, String message, MediaKind mediaKind,
			String mediaRef, Instant opensAt, Instant now) {
		this.id = Ids.newId();
		this.senderId = senderId;
		this.recipientId = recipientId;
		this.connectionId = connectionId;
		this.message = message;
		this.mediaKind = mediaKind;
		this.mediaRef = mediaRef;
		this.status = Status.SEALED;
		this.opensAt = opensAt;
		this.createdAt = now;
	}

	boolean isToSelf() {
		return connectionId == null;
	}

	void open(Instant now) {
		status = Status.OPENED;
		openedAt = now;
	}

	void cancel() {
		status = Status.CANCELLED;
	}

	void preserved(String durableRef) {
		this.durableMediaRef = durableRef;
	}

	/** The media to serve: the durable copy once made, the original until then. */
	String servedMediaRef() {
		return durableMediaRef != null ? durableMediaRef : mediaRef;
	}

	String getId() {
		return id;
	}

	String getSenderId() {
		return senderId;
	}

	String getRecipientId() {
		return recipientId;
	}

	String getConnectionId() {
		return connectionId;
	}

	String getMessage() {
		return message;
	}

	MediaKind getMediaKind() {
		return mediaKind;
	}

	String getMediaRef() {
		return mediaRef;
	}

	String getDurableMediaRef() {
		return durableMediaRef;
	}

	Status getStatus() {
		return status;
	}

	Instant getOpensAt() {
		return opensAt;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getOpenedAt() {
		return openedAt;
	}
}
