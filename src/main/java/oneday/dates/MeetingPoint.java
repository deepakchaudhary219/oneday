package oneday.dates;

import java.time.Instant;

import oneday.common.Ids;
import oneday.geo.Geohash;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A Safety-Verified Meeting Point (blueprint v2 §5.3): a public, well-lit, staffed venue checked by Trust &
 * Safety. Venues are public places, so their exact position is not personal data.
 */
@Entity
@Table(name = "meeting_points")
public class MeetingPoint {

	@Id
	private String id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MeetingPointCategory category;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false)
	private double lat;

	@Column(nullable = false)
	private double lon;

	@Column(nullable = false)
	private String area;

	private String safetyNotes;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false)
	private String verifiedBy;

	@Column(nullable = false)
	private Instant verifiedAt;

	protected MeetingPoint() {
	}

	MeetingPoint(String name, MeetingPointCategory category, String address, double lat, double lon,
			String safetyNotes, String verifiedBy, Instant now) {
		this.id = Ids.newId();
		this.name = name;
		this.category = category;
		this.address = address;
		this.lat = lat;
		this.lon = lon;
		this.area = Geohash.encode(lat, lon, Geohash.AREA_PRECISION);
		this.safetyNotes = safetyNotes;
		this.active = true;
		this.verifiedBy = verifiedBy;
		this.verifiedAt = now;
	}

	void deactivate() {
		active = false;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public MeetingPointCategory getCategory() {
		return category;
	}

	public String getAddress() {
		return address;
	}

	public double getLat() {
		return lat;
	}

	public double getLon() {
		return lon;
	}

	public String getSafetyNotes() {
		return safetyNotes;
	}

	public boolean isActive() {
		return active;
	}
}
