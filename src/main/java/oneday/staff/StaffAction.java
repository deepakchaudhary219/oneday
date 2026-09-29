package oneday.staff;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row per staff decision. Append-only: there is no code path that updates or deletes these. */
@Entity
@Table(name = "staff_actions")
public class StaffAction {

	@Id
	private String id;

	@Column(nullable = false)
	private String staffUserId;

	@Column(nullable = false)
	private String action;

	@Column(nullable = false)
	private String subjectType;

	@Column(nullable = false)
	private String subjectId;

	private String note;

	@Column(nullable = false)
	private Instant createdAt;

	protected StaffAction() {
	}

	StaffAction(String staffUserId, String action, String subjectType, String subjectId, String note, Instant now) {
		this.id = Ids.newId();
		this.staffUserId = staffUserId;
		this.action = action;
		this.subjectType = subjectType;
		this.subjectId = subjectId;
		this.note = note;
		this.createdAt = now;
	}

	public String getStaffUserId() {
		return staffUserId;
	}

	public String getAction() {
		return action;
	}

	public String getSubjectType() {
		return subjectType;
	}

	public String getSubjectId() {
		return subjectId;
	}

	public String getNote() {
		return note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
