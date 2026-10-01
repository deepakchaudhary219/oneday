package oneday.rightnow;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** "I'm up for {activity} for the next hour." Ends on its own; nothing about it outlives the session. */
@Entity
@Table(name = "right_now_sessions")
public class RightNowSession {

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false)
	private String activity;

	@Column(nullable = false)
	private Instant startsAt;

	@Column(nullable = false)
	private Instant endsAt;

	private Instant endedAt;

	protected RightNowSession() {
	}

	RightNowSession(String userId, String activity, Instant now, Instant endsAt) {
		this.id = Ids.newId();
		this.userId = userId;
		this.activity = activity;
		this.startsAt = now;
		this.endsAt = endsAt;
	}

	boolean isActive(Instant now) {
		return endedAt == null && now.isBefore(endsAt);
	}

	void end(Instant now) {
		if (endedAt == null) {
			endedAt = now;
		}
	}

	public String getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public String getActivity() {
		return activity;
	}

	public Instant getEndsAt() {
		return endsAt;
	}
}
