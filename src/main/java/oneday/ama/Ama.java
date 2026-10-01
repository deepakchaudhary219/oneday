package oneday.ama;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "amas")
class Ama {

	enum State {
		/** Questions are open from a day before the start. */
		UPCOMING, LIVE, ENDED, CANCELLED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String hostId;

	@Column(nullable = false)
	private String title;

	/** ISO 3166-2 home region, e.g. IN-KL; null when open to everyone. */
	private String corridorRegion;

	@Column(nullable = false)
	private Instant startsAt;

	@Column(nullable = false)
	private Instant endsAt;

	@Column(nullable = false)
	private boolean cancelled;

	@Column(nullable = false)
	private Instant createdAt;

	protected Ama() {
	}

	Ama(String hostId, String title, String corridorRegion, Instant startsAt, Instant endsAt, Instant now) {
		this.id = Ids.newId();
		this.hostId = hostId;
		this.title = title;
		this.corridorRegion = corridorRegion;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.createdAt = now;
	}

	State state(Instant now) {
		if (cancelled) {
			return State.CANCELLED;
		}
		if (now.isBefore(startsAt)) {
			return State.UPCOMING;
		}
		return now.isBefore(endsAt) ? State.LIVE : State.ENDED;
	}

	void cancel() {
		cancelled = true;
	}

	String getId() {
		return id;
	}

	String getHostId() {
		return hostId;
	}

	String getTitle() {
		return title;
	}

	String getCorridorRegion() {
		return corridorRegion;
	}

	Instant getStartsAt() {
		return startsAt;
	}

	Instant getEndsAt() {
		return endsAt;
	}
}
