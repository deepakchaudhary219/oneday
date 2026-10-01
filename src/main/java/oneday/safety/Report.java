package oneday.safety;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reports")
public class Report {

	public enum Status {
		OPEN, IN_REVIEW, ACTIONED, DISMISSED;

		public boolean isOpen() {
			return this == OPEN || this == IN_REVIEW;
		}
	}

	/** What Trust & Safety did about a report. */
	public enum Resolution {
		DISMISSED, WARNED, SUSPENDED
	}

	@Id
	private String id;

	/** Nulled if the reporter erases their account; the report itself is retained for safety/legal duty. */
	private String reporterId;

	@Column(nullable = false)
	private String reportedId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportCategory.Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private String targetType;

	private String details;

	/** The plaintext of a reported encrypted message whose franking commitment verified. */
	private String verifiedEvidence;

	@Column(nullable = false)
	private Instant createdAt;

	private String assigneeId;

	@Enumerated(EnumType.STRING)
	private Resolution resolution;

	private String resolutionNote;

	private String resolvedBy;

	private Instant resolvedAt;

	protected Report() {
	}

	Report(String reporterId, String reportedId, ReportCategory category, String targetType, String details,
			Instant now) {
		this.id = Ids.newId();
		this.reporterId = reporterId;
		this.reportedId = reportedId;
		this.category = category;
		this.priority = category.priority();
		this.status = Status.OPEN;
		this.targetType = targetType;
		this.details = details;
		this.createdAt = now;
	}

	void claim(String staffUserId) {
		this.status = Status.IN_REVIEW;
		this.assigneeId = staffUserId;
	}

	void resolve(Resolution resolution, String note, String staffUserId, Instant now) {
		this.status = resolution == Resolution.DISMISSED ? Status.DISMISSED : Status.ACTIONED;
		this.resolution = resolution;
		this.resolutionNote = note;
		this.resolvedBy = staffUserId;
		this.resolvedAt = now;
	}

	public String getId() {
		return id;
	}

	public String getTargetType() {
		return targetType;
	}

	public String getVerifiedEvidence() {
		return verifiedEvidence;
	}

	void attachVerifiedEvidence(String evidence) {
		this.verifiedEvidence = evidence;
	}

	public String getDetails() {
		return details;
	}

	public String getAssigneeId() {
		return assigneeId;
	}

	public Resolution getResolution() {
		return resolution;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public String getReporterId() {
		return reporterId;
	}

	public String getReportedId() {
		return reportedId;
	}

	public ReportCategory getCategory() {
		return category;
	}

	public ReportCategory.Priority getPriority() {
		return priority;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
