package oneday.live;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_viewers")
class LiveViewer {

	@Embeddable
	record Key(@Column(name = "session_id") String sessionId, @Column(name = "user_id") String userId) {
	}

	@EmbeddedId
	private Key key;

	@Column(nullable = false)
	private Instant joinedAt;

	protected LiveViewer() {
	}

	LiveViewer(String sessionId, String userId, Instant now) {
		this.key = new Key(sessionId, userId);
		this.joinedAt = now;
	}
}
