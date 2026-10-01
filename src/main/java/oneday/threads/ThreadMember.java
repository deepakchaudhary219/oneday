package oneday.threads;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "thread_members")
class ThreadMember {

	enum Status {
		INVITED, IN, DECLINED, LEFT, REMOVED
	}

	@Embeddable
	record Key(@Column(name = "thread_id") String threadId, @Column(name = "user_id") String userId) {
	}

	@EmbeddedId
	private Key key;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private String invitedBy;

	@Column(nullable = false)
	private Instant updatedAt;

	protected ThreadMember() {
	}

	ThreadMember(String threadId, String userId, Status status, String invitedBy, Instant now) {
		this.key = new Key(threadId, userId);
		this.status = status;
		this.invitedBy = invitedBy;
		this.updatedAt = now;
	}

	void setStatus(Status status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}

	boolean isIn() {
		return status == Status.IN;
	}

	String userId() {
		return key.userId();
	}

	String threadId() {
		return key.threadId();
	}

	Status getStatus() {
		return status;
	}
}
