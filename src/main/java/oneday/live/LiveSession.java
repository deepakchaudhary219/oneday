package oneday.live;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_sessions")
class LiveSession {

	@Id
	private String id;

	@Column(nullable = false)
	private String hostId;

	/** Null: all of the host's Connections. Otherwise the members of this Collaborative Thread. */
	private String threadId;

	private String title;

	@Column(nullable = false)
	private Instant startedAt;

	private Instant endedAt;

	private String endReason;

	protected LiveSession() {
	}

	LiveSession(String hostId, String threadId, String title, Instant now) {
		this.id = Ids.newId();
		this.hostId = hostId;
		this.threadId = threadId;
		this.title = title;
		this.startedAt = now;
	}

	boolean isLive() {
		return endedAt == null;
	}

	void end(String reason, Instant now) {
		endedAt = now;
		endReason = reason;
	}

	String getId() {
		return id;
	}

	String getHostId() {
		return hostId;
	}

	String getThreadId() {
		return threadId;
	}

	String getTitle() {
		return title;
	}

	Instant getStartedAt() {
		return startedAt;
	}

	Instant getEndedAt() {
		return endedAt;
	}

	String getEndReason() {
		return endReason;
	}
}
