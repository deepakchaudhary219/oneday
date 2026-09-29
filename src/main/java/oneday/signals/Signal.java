package oneday.signals;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Layer 1 of the Layered Reveal: a quiet, private indication of interest. It is never a message and
 * never triggers a real-time notification; it waits in the recipient's digest until the Reaction
 * Window closes (blueprint v2 §4).
 */
@Entity
@Table(name = "signals")
public class Signal {

	public enum Status {
		PENDING, REVEALED, PASSED, ARCHIVED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String senderId;

	@Column(nullable = false)
	private String recipientId;

	@Column(nullable = false)
	private String momentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Reaction reaction;

	/** One of the recipient's own activities, the only "content" a sender can attach. */
	private String activityRef;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant windowExpiresAt;

	private Instant resolvedAt;

	protected Signal() {
	}

	Signal(String senderId, String recipientId, String momentId, Reaction reaction, String activityRef, Instant now,
			Instant windowExpiresAt) {
		this.id = Ids.newId();
		this.senderId = senderId;
		this.recipientId = recipientId;
		this.momentId = momentId;
		this.reaction = reaction;
		this.activityRef = activityRef;
		this.status = Status.PENDING;
		this.createdAt = now;
		this.windowExpiresAt = windowExpiresAt;
	}

	public boolean isActionable(Instant now) {
		return status == Status.PENDING && windowExpiresAt.isAfter(now);
	}

	void resolve(Status outcome, Instant now) {
		this.status = outcome;
		this.resolvedAt = now;
	}

	public String getId() {
		return id;
	}

	public String getSenderId() {
		return senderId;
	}

	public String getRecipientId() {
		return recipientId;
	}

	public String getMomentId() {
		return momentId;
	}

	public Reaction getReaction() {
		return reaction;
	}

	public String getActivityRef() {
		return activityRef;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getWindowExpiresAt() {
		return windowExpiresAt;
	}
}
