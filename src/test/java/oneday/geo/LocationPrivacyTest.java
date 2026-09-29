package oneday.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import oneday.config.OneDayProperties;
import oneday.geo.LocationPrivacy.Placement;
import oneday.geo.LocationPrivacy.Precision;
import oneday.support.MutableClock;

import org.junit.jupiter.api.Test;

class LocationPrivacyTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-09-29T10:00:00Z"));

	private final LocationPrivacy privacy = new LocationPrivacy(new OneDayProperties(null, null, null,
			new OneDayProperties.Location("unit-test-location-secret-0123456789abcdef", Duration.ofSeconds(30), 1000,
					12),
			null, null, null), clock);

	private final GeoCell koramangala = GeoCell.snap(12.9352, 77.6245);

	private final GeoCell indiranagar = GeoCell.snap(12.9719, 77.6412);

	@Test
	void jitterIsStableAndSymmetricForAPairWithinADay() {
		double first = privacy.jitterKm("alice", "bob");
		for (int i = 0; i < 50; i++) {
			assertThat(privacy.jitterKm("alice", "bob")).isEqualTo(first);
		}
		assertThat(privacy.jitterKm("bob", "alice")).isEqualTo(first);
		assertThat(Math.abs(first)).isLessThanOrEqualTo(LocationPrivacy.MAX_JITTER_KM);
	}

	@Test
	void jitterDiffersAcrossPairsAndDays() {
		Set<Double> values = new HashSet<>();
		for (int i = 0; i < 20; i++) {
			values.add(privacy.jitterKm("viewer", "target-" + i));
		}
		assertThat(values).hasSizeGreaterThan(15);
		double today = privacy.jitterKm("alice", "bob");
		clock.advance(Duration.ofDays(1));
		assertThat(privacy.jitterKm("alice", "bob")).isNotEqualTo(today);
	}

	@Test
	void repeatedQueriesReturnTheSameBandSoAveragingLearnsNothing() {
		Set<Placement> seen = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			privacy.place(koramangala, indiranagar, "v", "t", Precision.BAND, false, 15).ifPresent(seen::add);
		}
		assertThat(seen).hasSize(1);
		Placement placement = seen.iterator().next();
		assertThat(placement.band()).isEqualTo(DistanceBand.KM_1_TO_5);
		assertThat(placement.direction()).isIn(Direction.N, Direction.NE);
	}

	@Test
	void sameCellGivesNoDirection() {
		Placement p = privacy.place(koramangala, koramangala, "v", "t", Precision.BAND, false, 5).orElseThrow();
		assertThat(p.band()).isEqualTo(DistanceBand.UNDER_1_KM);
		assertThat(p.direction()).isEqualTo(Direction.NONE);
	}

	@Test
	void cityPrecisionAndSafeZonesWithholdBandAndDirection() {
		assertThat(privacy.place(koramangala, indiranagar, "v", "t", Precision.CITY, false, 40).orElseThrow())
			.isEqualTo(new Placement(DistanceBand.IN_CITY, Direction.NONE));
		assertThat(privacy.place(koramangala, indiranagar, "v", "t", Precision.BAND, true, 15).orElseThrow())
			.isEqualTo(new Placement(DistanceBand.NEARBY_AREA, Direction.NONE));
	}

	@Test
	void targetsOutsideTheRadiusAreNotPlacedAtAll() {
		GeoCell mysore = GeoCell.snap(12.2958, 76.6394);
		assertThat(privacy.place(koramangala, mysore, "v", "t", Precision.BAND, false, 15)).isEmpty();
		assertThat(privacy.place(koramangala, indiranagar, "v", "t", Precision.BAND, false, 1)).isEmpty();
	}
}
