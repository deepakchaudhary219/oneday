package oneday.geo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import oneday.common.ApiException;
import oneday.config.OneDayProperties;
import oneday.consent.ConsentLedger;
import oneday.consent.ConsentPurpose;
import oneday.identity.UserGuard;
import oneday.profile.Profile;
import oneday.profile.ProfileService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Foreground-only location ingest (tech arch v2 §5.2). Raw coordinates are snapped on arrival and
 * discarded; anti-spoofing checks reject teleports, rapid-fire updates and cell-hopping probes.
 */
@Service
public class LocationService {

	/** Neighbouring-cell jumps from GPS noise must not trip the velocity check. */
	private static final double VELOCITY_CHECK_MIN_KM = 2.0;

	private final UserLocationRepository locations;

	private final ProfileService profiles;

	private final UserGuard guard;

	private final ProbeBudget probeBudget;

	private final Clock clock;

	private final OneDayProperties.Location settings;

	private final ConsentLedger consents;

	public LocationService(UserLocationRepository locations, ProfileService profiles, UserGuard guard,
			ProbeBudget probeBudget, Clock clock, OneDayProperties properties, ConsentLedger consents) {
		this.consents = consents;
		this.locations = locations;
		this.profiles = profiles;
		this.guard = guard;
		this.probeBudget = probeBudget;
		this.clock = clock;
		this.settings = properties.location();
	}

	@Transactional
	public LocationView update(String userId, double rawLat, double rawLon) {
		guard.requireActive(userId);
		consents.affirm(userId, ConsentPurpose.LOCATION_DISCOVERY);
		GeoCell next = GeoCell.snap(rawLat, rawLon);
		Instant now = clock.instant();
		Optional<UserLocation> existing = locations.findById(userId);
		if (existing.isPresent()) {
			UserLocation current = existing.get();
			if (current.getCell().equals(next.cell())) {
				return view(current);
			}
			Duration elapsed = Duration.between(current.getUpdatedAt(), now);
			if (elapsed.compareTo(settings.minUpdateInterval()) < 0) {
				throw ApiException.tooManyRequests("LOCATION_UPDATE_TOO_FREQUENT", "Location updates are rate-limited");
			}
			double km = current.toCell().distanceKm(next);
			double hours = Math.max(elapsed.toMillis(), 1) / 3_600_000.0;
			if (km > VELOCITY_CHECK_MIN_KM && km / hours > settings.maxSpeedKmh()) {
				throw ApiException.unprocessable("LOCATION_IMPLAUSIBLE",
						"That location change isn't physically possible. OneDay only uses where you really are.");
			}
		}
		if (!probeBudget.tryVisit(userId, next.cell())) {
			throw ApiException.tooManyRequests("LOCATION_PROBE_LIMIT", "Too many location changes this hour");
		}
		UserLocation location = existing.orElseGet(() -> new UserLocation(userId, next, now));
		location.moveTo(next, now);
		return view(locations.save(location));
	}

	/**
	 * DPDP withdrawal of {@code LOCATION_DISCOVERY}: like {@link #pause} but allowed in any account state. The
	 * probe budget is anti-abuse state, not discovery data, so withdrawing can't be used to reset it.
	 */
	@Transactional
	public void withdraw(String userId) {
		locations.deleteById(userId);
	}

	/** Stops sharing: deletes the stored cell, which removes the user from discovery at once. */
	@Transactional
	public void pause(String userId) {
		guard.requireActive(userId);
		locations.deleteById(userId);
	}

	/** Erasure: drop the stored cell and any in-memory probe history. */
	@Transactional
	public void forget(String userId) {
		locations.deleteById(userId);
		probeBudget.forget(userId);
	}

	@Transactional
	public LocationView markSafeZoneHere(String userId) {
		UserLocation location = locations.findById(userId).orElseThrow(LocationService::locationRequired);
		profiles.require(userId).setSafeZonePrefix(location.toCell().area());
		return view(location);
	}

	@Transactional
	public void clearSafeZone(String userId) {
		profiles.require(userId).setSafeZonePrefix(null);
	}

	@Transactional(readOnly = true)
	public Optional<LocationView> current(String userId) {
		return locations.findById(userId).map(this::view);
	}

	public Optional<GeoCell> currentCell(String userId) {
		return locations.findById(userId).map(UserLocation::toCell);
	}

	/** Current cells for the given users; users who paused sharing are simply absent. */
	@Transactional(readOnly = true)
	public Map<String, GeoCell> currentCells(Collection<String> userIds) {
		return locations.findAllById(userIds)
			.stream()
			.collect(Collectors.toMap(UserLocation::getUserId, UserLocation::toCell));
	}

	public GeoCell requireCurrentCell(String userId) {
		return currentCell(userId).orElseThrow(LocationService::locationRequired);
	}

	private LocationView view(UserLocation location) {
		Profile profile = profiles.require(location.getUserId());
		return new LocationView(location.getCell(), true, profile.isInSafeZone(location.getCell()),
				location.getUpdatedAt());
	}

	static ApiException locationRequired() {
		return ApiException.conflict("LOCATION_REQUIRED", "Share your location (only while the app is open) first");
	}

	/** The owner's own view: their current cell id (~0.7 km²), never anyone else's. */
	/** How many people currently share a location within {@code radiusKm} of a point (city density, staff only). */
	@Transactional(readOnly = true)
	public long countSharingWithin(double lat, double lon, double radiusKm) {
		double dLat = radiusKm / 111.0;
		double dLon = radiusKm / (111.0 * Math.max(Math.cos(Math.toRadians(lat)), 0.01));
		GeoCell center = new GeoCell("", lat, lon);
		return locations.cellsInBox(lat - dLat, lat + dLat, lon - dLon, lon + dLon)
			.stream()
			.filter(row -> center.distanceKm(new GeoCell("", (Double) row[0], (Double) row[1])) <= radiusKm)
			.count();
	}

	public record LocationView(String area, boolean sharing, boolean insideSafeZone, Instant updatedAt) {
	}
}
