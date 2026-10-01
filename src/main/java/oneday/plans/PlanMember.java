package oneday.plans;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_members")
public class PlanMember {

	public enum Status {
		HOST, REQUESTED, APPROVED, DECLINED, LEFT
	}

	@EmbeddedId
	private Key key;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant createdAt;

	protected PlanMember() {
	}

	PlanMember(String planId, String userId, Status status, Instant now) {
		this.key = new Key(planId, userId);
		this.status = status;
		this.createdAt = now;
	}

	boolean isIn() {
		return status == Status.HOST || status == Status.APPROVED;
	}

	void setStatus(Status status) {
		this.status = status;
	}

	public String getPlanId() {
		return key.planId();
	}

	public String getUserId() {
		return key.userId();
	}

	public Status getStatus() {
		return status;
	}

	@Embeddable
	public record Key(@Column(name = "plan_id") String planId, @Column(name = "user_id") String userId)
			implements Serializable {
	}
}
