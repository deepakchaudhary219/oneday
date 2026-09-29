package oneday.verification;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The v1 architecture's TrustRecord: every liveness attempt and how it was decided. */
@Entity
@Table(name = "verification_attempts")
public class VerificationAttempt {

	public enum Outcome {
		PASSED, FAILED_LIVENESS, LOW_CONFIDENCE, AGE_MISMATCH, POSSIBLE_MINOR
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false)
	private String provider;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Outcome outcome;

	private int estimatedAge;

	private double confidence;

	@Column(nullable = false)
	private Instant createdAt;

	protected VerificationAttempt() {
	}

	VerificationAttempt(String userId, String provider, Outcome outcome, int estimatedAge, double confidence,
			Instant now) {
		this.id = Ids.newId();
		this.userId = userId;
		this.provider = provider;
		this.outcome = outcome;
		this.estimatedAge = estimatedAge;
		this.confidence = confidence;
		this.createdAt = now;
	}

	public String getUserId() {
		return userId;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public int getEstimatedAge() {
		return estimatedAge;
	}

	public double getConfidence() {
		return confidence;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
