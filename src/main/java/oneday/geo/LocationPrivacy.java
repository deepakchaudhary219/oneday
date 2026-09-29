package oneday.geo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import oneday.config.OneDayProperties;

import org.springframework.stereotype.Component;

/**
 * Turns two snapped cells into what one person may see about the other (tech arch v2 §5.2).
 *
 * <ul>
 * <li>Distances are measured between cell centres, so moving inside a cell reveals nothing.</li>
 * <li>Band edges carry a jitter that is <em>stable</em> for a pair within a day (keyed HMAC). Fresh random
 * noise per read would average out over repeated queries; stable noise does not.</li>
 * <li>City-scope and Safe-Zone placements drop band and direction entirely.</li>
 * </ul>
 */
@Component
public class LocationPrivacy {

	static final double MAX_JITTER_KM = 0.25;

	private final SecretKeySpec key;

	private final Clock clock;

	public LocationPrivacy(OneDayProperties properties, Clock clock) {
		String secret = properties.location().jitterSecret();
		byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("oneday.location.jitter-secret must be set to at least 32 bytes");
		}
		this.key = new SecretKeySpec(bytes, "HmacSHA256");
		this.clock = clock;
	}

	public enum Precision {
		/** Distance band + coarse direction. */
		BAND,
		/** "In your city" only. */
		CITY
	}

	public record Placement(DistanceBand band, Direction direction) {
	}

	/**
	 * @return the placement, or empty when the target is outside {@code radiusKm}
	 */
	public Optional<Placement> place(GeoCell viewer, GeoCell target, String viewerId, String targetId,
			Precision precision, boolean targetInSafeZone, double radiusKm) {
		double distance = viewer.distanceKm(target);
		double jitter = jitterKm(viewerId, targetId);
		if (distance > radiusKm + jitter) {
			return Optional.empty();
		}
		if (targetInSafeZone) {
			return Optional.of(new Placement(DistanceBand.NEARBY_AREA, Direction.NONE));
		}
		if (precision == Precision.CITY) {
			return Optional.of(new Placement(DistanceBand.IN_CITY, Direction.NONE));
		}
		DistanceBand band;
		if (distance < 1 + jitter) {
			band = DistanceBand.UNDER_1_KM;
		}
		else if (distance < 5 + jitter) {
			band = DistanceBand.KM_1_TO_5;
		}
		else {
			band = DistanceBand.KM_5_TO_15;
		}
		Direction direction = viewer.cell().equals(target.cell()) ? Direction.NONE
				: Direction.fromBearing(GeoMath.bearingDeg(viewer.lat(), viewer.lon(), target.lat(), target.lon()));
		return Optional.of(new Placement(band, direction));
	}

	/** Symmetric for the pair, stable within a UTC day, in [-MAX_JITTER_KM, +MAX_JITTER_KM). */
	double jitterKm(String a, String b) {
		String lo = a.compareTo(b) <= 0 ? a : b;
		String hi = a.compareTo(b) <= 0 ? b : a;
		long day = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toEpochDay();
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			byte[] digest = mac.doFinal((lo + '|' + hi + '|' + day).getBytes(StandardCharsets.UTF_8));
			long bits = ByteBuffer.wrap(digest).getLong() >>> 11;
			double unit = bits / (double) (1L << 53);
			return (unit * 2 - 1) * MAX_JITTER_KM;
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 unavailable", ex);
		}
	}
}
