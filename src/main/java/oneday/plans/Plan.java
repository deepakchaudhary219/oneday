package oneday.plans;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A small public meet-up at a Safety-Verified Meeting Point. Its position is the venue's (a public place). */
@Entity
@Table(name = "plans")
public class Plan {

	public enum Status {
		OPEN, CANCELLED, ENDED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String hostId;

	@Column(nullable = false)
	private String meetingPointId;

	@Column(nullable = false)
	private String activity;

	@Column(nullable = false)
	private String title;

	/** When set, only people from this home region can see and join (a Roots plan). */
	private String rootsRegion;

	@Column(nullable = false)
	private int capacity;

	@Column(nullable = false)
	private Instant startsAt;

	@Column(nullable = false)
	private Instant endsAt;

	@Column(nullable = false)
	private double lat;

	@Column(nullable = false)
	private double lon;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant createdAt;

	@Version
	@Column(name = "row_version")
	private long version;

	protected Plan() {
	}

	Plan(String hostId, String meetingPointId, String activity, String title, String rootsRegion, int capacity,
			Instant startsAt, Instant endsAt, double lat, double lon, Instant now) {
		this.id = Ids.newId();
		this.hostId = hostId;
		this.meetingPointId = meetingPointId;
		this.activity = activity;
		this.title = title;
		this.rootsRegion = rootsRegion;
		this.capacity = capacity;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.lat = lat;
		this.lon = lon;
		this.status = Status.OPEN;
		this.createdAt = now;
	}

	boolean isOpen(Instant now) {
		return status == Status.OPEN && now.isBefore(endsAt);
	}

	void cancel() {
		status = Status.CANCELLED;
	}

	void end() {
		if (status == Status.OPEN) {
			status = Status.ENDED;
		}
	}

	public String getId() {
		return id;
	}

	public String getHostId() {
		return hostId;
	}

	public String getMeetingPointId() {
		return meetingPointId;
	}

	public String getActivity() {
		return activity;
	}

	public String getTitle() {
		return title;
	}

	public String getRootsRegion() {
		return rootsRegion;
	}

	public int getCapacity() {
		return capacity;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public double getLat() {
		return lat;
	}

	public double getLon() {
		return lon;
	}

	public Status getStatus() {
		return status;
	}
}
