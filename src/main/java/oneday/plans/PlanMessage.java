package oneday.plans;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A message in a plan's Room. Rooms are temporary: deleted a day after the plan ends. */
@Entity
@Table(name = "plan_messages")
public class PlanMessage {

	@Id
	private String id;

	@Column(nullable = false)
	private String planId;

	@Column(nullable = false)
	private String senderId;

	@Column(nullable = false)
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	protected PlanMessage() {
	}

	PlanMessage(String planId, String senderId, String body, Instant now) {
		this.id = Ids.newId();
		this.planId = planId;
		this.senderId = senderId;
		this.body = body;
		this.createdAt = now;
	}

	public String getId() {
		return id;
	}

	public String getPlanId() {
		return planId;
	}

	public String getSenderId() {
		return senderId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
