package oneday.cities;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import oneday.common.ApiException;
import oneday.geo.GeoCell;
import oneday.geo.LocationService;
import oneday.staff.StaffAudit;
import oneday.staff.StaffDirectory;
import oneday.staff.StaffRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Multi-city (v3; docs/07-v3-features.md). OneDay launches city by city: each city has a stage and the features
 * its density supports. A person's city is the nearest one whose radius covers their snapped cell; it is
 * computed on demand and never stored. The registry is small (one row per city), so it is read whole.
 */
@Service
public class CityGates {

	private static final Pattern ID = Pattern.compile("[a-z0-9-]{2,40}");

	private final CityRepository cities;

	private final LocationService locations;

	private final StaffDirectory staff;

	private final StaffAudit audit;

	private final Clock clock;

	public CityGates(CityRepository cities, LocationService locations, StaffDirectory staff, StaffAudit audit,
			Clock clock) {
		this.cities = cities;
		this.locations = locations;
		this.staff = staff;
		this.audit = audit;
		this.clock = clock;
	}

	/** Whether this feature is open in the person's current city (false when they share no location). */
	@Transactional(readOnly = true)
	public boolean isEnabled(String userId, CityFeature feature) {
		return cityOf(userId).map(c -> c.activeFeatures().contains(feature)).orElse(false);
	}

	@Transactional(readOnly = true)
	public Optional<CityView> current(String userId) {
		return cityOf(userId).map(CityGates::view);
	}

	@Transactional(readOnly = true)
	public List<CityView> list(String staffId) {
		staff.require(staffId, StaffRole.MODERATOR);
		return cities.findAll().stream().sorted(Comparator.comparing(City::getName)).map(CityGates::view).toList();
	}

	@Transactional
	public CityView upsert(String staffId, String rawId, CityRequest request) {
		staff.require(staffId, StaffRole.ADMIN);
		String id = rawId == null ? "" : rawId.strip().toLowerCase(Locale.ROOT);
		if (!ID.matcher(id).matches() || request.name() == null || request.name().isBlank()
				|| request.countryCode() == null || !request.countryCode().matches("[A-Za-z]{2}")) {
			throw ApiException.badRequest("INVALID_CITY", "id (a-z, 0-9, -), name and a 2-letter country are required");
		}
		if (Math.abs(request.lat()) > 90 || Math.abs(request.lon()) > 180 || request.radiusKm() < 2
				|| request.radiusKm() > 80) {
			throw ApiException.badRequest("INVALID_AREA", "A city is a centre and a 2-80 km radius");
		}
		City city = cities.findById(id).orElseGet(() -> new City(id));
		city.update(request.name().strip(), request.countryCode().toUpperCase(Locale.ROOT), request.lat(),
				request.lon(), request.radiusKm(), request.stage() == null ? City.Stage.PILOT : request.stage(),
				request.features() == null ? Set.of() : request.features(), clock.instant());
		cities.save(city);
		audit.record(staffId, "CITY_UPDATED", "CITY", id, city.getStage() + " " + city.features());
		return view(city);
	}

	/** Density for the launch decision: people sharing location in the city now. */
	@Transactional(readOnly = true)
	public Density density(String staffId, String id) {
		staff.require(staffId, StaffRole.MODERATOR);
		City city = cities.findById(id).orElseThrow(() -> ApiException.notFound("City"));
		long sharing = locations.countSharingWithin(city.getCenterLat(), city.getCenterLon(), city.getRadiusKm());
		double area = Math.PI * city.getRadiusKm() * city.getRadiusKm();
		return new Density(city.getId(), sharing, Math.round(sharing / area * 100.0) / 100.0);
	}

	private Optional<City> cityOf(String userId) {
		Optional<GeoCell> cell = locations.currentCell(userId);
		if (cell.isEmpty()) {
			return Optional.empty();
		}
		GeoCell here = cell.get();
		return cities.findAll()
			.stream()
			.filter(c -> here.distanceKm(new GeoCell("", c.getCenterLat(), c.getCenterLon())) <= c.getRadiusKm())
			.min(Comparator.comparingDouble(c -> here.distanceKm(new GeoCell("", c.getCenterLat(), c.getCenterLon()))));
	}

	private static CityView view(City c) {
		return new CityView(c.getId(), c.getName(), c.getCountryCode(), c.getStage().name(), c.activeFeatures());
	}

	public record CityView(String id, String name, String countryCode, String stage, Set<CityFeature> features) {
	}

	public record CityRequest(String name, String countryCode, double lat, double lon, double radiusKm,
			City.Stage stage, Set<CityFeature> features) {
	}

	/** {@code perSquareKm}: people sharing location per km², the density gate's measure. */
	public record Density(String cityId, long sharingNow, double perSquareKm) {
	}
}
