package oneday.figures;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "figure_follows")
class FigureFollow {

	@Embeddable
	record Key(@Column(name = "follower_id") String followerId, @Column(name = "figure_id") String figureId) {
	}

	@EmbeddedId
	private Key key;

	@Column(nullable = false)
	private Instant createdAt;

	protected FigureFollow() {
	}

	FigureFollow(String followerId, String figureId, Instant now) {
		this.key = new Key(followerId, figureId);
		this.createdAt = now;
	}

	String figureId() {
		return key.figureId();
	}

	Instant getCreatedAt() {
		return createdAt;
	}
}
