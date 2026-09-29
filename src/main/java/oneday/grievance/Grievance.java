package oneday.grievance;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A complaint or appeal to the Grievance Officer. Acknowledged when filed; answered by {@code resolveBy}. */
@Entity
@Table(name = "grievances")
public class Grievance {

	public enum Status {
		OPEN, RESOLVED
	}

	public enum Outcome {
		UPHELD, PARTLY_UPHELD, NOT_UPHELD
	}

	@Id
	private String id;

	@Column(nullable = false, unique = true)
	private String reference;

	@Column(nullable = false)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GrievanceCategory category;

	private String subjectRef;

	@Column(nullable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant resolveBy;

	private Instant resolvedAt;

	private String resolvedBy;

	@Enumerated(EnumType.STRING)
	private Outcome outcome;

	private String response;

	protected Grievance() {
	}

	Grievance(String reference, String userId, GrievanceCategory category, String subjectRef, String description,
			Instant now) {
		this.id = Ids.newId();
		this.reference = reference;
		this.userId = userId;
		this.category = category;
		this.subjectRef = subjectRef;
		this.description = description;
		this.status = Status.OPEN;
		this.createdAt = now;
		this.resolveBy = now.plus(category.resolveWithin());
	}

	void resolve(Outcome outcome, String response, String staffId, Instant now) {
		this.status = Status.RESOLVED;
		this.outcome = outcome;
		this.response = response;
		this.resolvedBy = staffId;
		this.resolvedAt = now;
	}

	public boolean isOverdue(Instant now) {
		return status == Status.OPEN && now.isAfter(resolveBy);
	}

	public String getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public String getUserId() {
		return userId;
	}

	public GrievanceCategory getCategory() {
		return category;
	}

	public String getSubjectRef() {
		return subjectRef;
	}

	public String getDescription() {
		return description;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getResolveBy() {
		return resolveBy;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public String getResponse() {
		return response;
	}
}
