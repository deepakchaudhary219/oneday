package oneday.wellbeing;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A person had at least one meaningful, two-way interaction in this ISO week. */
@Entity
@Table(name = "weekly_actives")
public class WeeklyActive {

	@EmbeddedId
	private Key key;

	@Column(nullable = false)
	private Instant firstAt;

	protected WeeklyActive() {
	}

	WeeklyActive(String userId, LocalDate weekStart, Instant at) {
		this.key = new Key(userId, weekStart);
		this.firstAt = at;
	}

	@Embeddable
	public record Key(@Column(name = "user_id") String userId, @Column(name = "week_start") LocalDate weekStart)
			implements Serializable {
	}
}
