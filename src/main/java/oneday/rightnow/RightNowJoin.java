package oneday.rightnow;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** "I'm up for it too." Declines are silent: the joiner never learns the difference from no answer. */
@Entity
@Table(name = "right_now_joins")
public class RightNowJoin {

	public enum Status {
		PENDING, ACCEPTED, DECLINED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String sessionId;

	@Column(nullable = false)
	private String joinerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant createdAt;

	protected RightNowJoin() {
	}

	RightNowJoin(String sessionId, String joinerId, Instant now) {
		this.id = Ids.newId();
		this.sessionId = sessionId;
		this.joinerId = joinerId;
		this.status = Status.PENDING;
		this.createdAt = now;
	}

	void resolve(Status to) {
		status = to;
	}

	public String getId() {
		return id;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getJoinerId() {
		return joinerId;
	}

	public Status getStatus() {
		return status;
	}
}
