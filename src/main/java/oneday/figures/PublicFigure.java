package oneday.figures;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "public_figures")
class PublicFigure {

	enum Status {
		PENDING, APPROVED, REJECTED, REVOKED
	}

	@Id
	private String userId;

	@Column(nullable = false)
	private String handle;

	@Column(nullable = false)
	private String publicName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FigureCategory category;

	private String bio;

	@Column(nullable = false)
	private String evidence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant appliedAt;

	private Instant decidedAt;

	private String decidedBy;

	protected PublicFigure() {
	}

	PublicFigure(String userId, Instant now) {
		this.userId = userId;
		this.appliedAt = now;
	}

	void apply(String handle, String publicName, FigureCategory category, String bio, String evidence, Instant now) {
		this.handle = handle;
		this.publicName = publicName;
		this.category = category;
		this.bio = bio;
		this.evidence = evidence;
		this.status = Status.PENDING;
		this.appliedAt = now;
		this.decidedAt = null;
		this.decidedBy = null;
	}

	void decide(Status status, String staffId, Instant now) {
		this.status = status;
		this.decidedBy = staffId;
		this.decidedAt = now;
	}

	boolean isApproved() {
		return status == Status.APPROVED;
	}

	String getUserId() {
		return userId;
	}

	String getHandle() {
		return handle;
	}

	String getPublicName() {
		return publicName;
	}

	FigureCategory getCategory() {
		return category;
	}

	String getBio() {
		return bio;
	}

	String getEvidence() {
		return evidence;
	}

	Status getStatus() {
		return status;
	}

	Instant getAppliedAt() {
		return appliedAt;
	}

	Instant getDecidedAt() {
		return decidedAt;
	}
}
