package oneday.geo;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The user's <em>current</em> snapped cell and nothing else: no raw coordinates, no history. Deleting
 * the row (pause) removes the user and their moments from discovery immediately.
 */
@Entity
@Table(name = "user_locations")
public class UserLocation {

	@Id
	private String userId;

	@Column(nullable = false)
	private String cell;

	@Column(nullable = false)
	private double cellLat;

	@Column(nullable = false)
	private double cellLon;

	@Column(nullable = false)
	private Instant updatedAt;

	protected UserLocation() {
	}

	public UserLocation(String userId, GeoCell cell, Instant now) {
		this.userId = userId;
		moveTo(cell, now);
	}

	public void moveTo(GeoCell cell, Instant now) {
		this.cell = cell.cell();
		this.cellLat = cell.lat();
		this.cellLon = cell.lon();
		this.updatedAt = now;
	}

	public GeoCell toCell() {
		return new GeoCell(cell, cellLat, cellLon);
	}

	public String getUserId() {
		return userId;
	}

	public String getCell() {
		return cell;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
