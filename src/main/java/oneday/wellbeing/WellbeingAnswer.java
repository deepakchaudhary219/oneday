package oneday.wellbeing;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One answer to "Was your time on OneDay well spent?". */
@Entity
@Table(name = "wellbeing_answers")
public class WellbeingAnswer {

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false)
	private boolean wellSpent;

	@Column(nullable = false)
	private Instant createdAt;

	protected WellbeingAnswer() {
	}

	WellbeingAnswer(String userId, boolean wellSpent, Instant now) {
		this.id = Ids.newId();
		this.userId = userId;
		this.wellSpent = wellSpent;
		this.createdAt = now;
	}

	public boolean isWellSpent() {
		return wellSpent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
