package oneday.cities;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cities")
public class City {

	public enum Stage {
		/** Seeded, invite-led; core features only. */
		PILOT,
		/** Open to everyone in the city. */
		OPEN,
		/** Paused (e.g. while safety operations can't cover it). */
		PAUSED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String countryCode;

	@Column(nullable = false)
	private double centerLat;

	@Column(nullable = false)
	private double centerLon;

	@Column(nullable = false)
	private double radiusKm;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stage stage;

	/** Comma-separated {@link CityFeature} names. */
	@Column(nullable = false)
	private String features;

	@Column(nullable = false)
	private Instant updatedAt;

	protected City() {
	}

	City(String id) {
		this.id = id;
	}

	void update(String name, String countryCode, double lat, double lon, double radiusKm, Stage stage,
			Set<CityFeature> features, Instant now) {
		this.name = name;
		this.countryCode = countryCode;
		this.centerLat = lat;
		this.centerLon = lon;
		this.radiusKm = radiusKm;
		this.stage = stage;
		this.features = features.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
		this.updatedAt = now;
	}

	Set<CityFeature> features() {
		EnumSet<CityFeature> out = EnumSet.noneOf(CityFeature.class);
		Arrays.stream(features.split(",")).filter(f -> !f.isBlank()).forEach(f -> out.add(CityFeature.valueOf(f)));
		return out;
	}

	/** Features in force: none while paused. */
	Set<CityFeature> activeFeatures() {
		return stage == Stage.PAUSED ? EnumSet.noneOf(CityFeature.class) : features();
	}

	String getId() {
		return id;
	}

	String getName() {
		return name;
	}

	String getCountryCode() {
		return countryCode;
	}

	double getCenterLat() {
		return centerLat;
	}

	double getCenterLon() {
		return centerLon;
	}

	double getRadiusKm() {
		return radiusKm;
	}

	Stage getStage() {
		return stage;
	}
}
