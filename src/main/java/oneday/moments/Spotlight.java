package oneday.moments;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "spotlights")
class Spotlight {

	enum Status {
		PENDING, ACCEPTED, DECLINED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String rootMomentId;

	@Column(nullable = false)
	private String replyMomentId;

	@Column(nullable = false)
	private String ownerId;

	@Column(nullable = false)
	private String replierId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant createdAt;

	protected Spotlight() {
	}

	Spotlight(String rootMomentId, String replyMomentId, String ownerId, String replierId, Instant now) {
		this.id = Ids.newId();
		this.rootMomentId = rootMomentId;
		this.replyMomentId = replyMomentId;
		this.ownerId = ownerId;
		this.replierId = replierId;
		this.status = Status.PENDING;
		this.createdAt = now;
	}

	void setStatus(Status status) {
		this.status = status;
	}

	String getId() {
		return id;
	}

	String getRootMomentId() {
		return rootMomentId;
	}

	String getReplyMomentId() {
		return replyMomentId;
	}

	String getOwnerId() {
		return ownerId;
	}

	String getReplierId() {
		return replierId;
	}

	Status getStatus() {
		return status;
	}
}
