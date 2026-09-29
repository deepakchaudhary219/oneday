package oneday.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeohashTest {

	@Test
	void encodesTheReferenceVector() {
		assertThat(Geohash.encode(57.64911, 10.40744, 11)).isEqualTo("u4pruydqqvj");
	}

	@Test
	void centreRoundTripsToTheSameCell() {
		String cell = Geohash.encode(12.9352, 77.6245, Geohash.CELL_PRECISION);
		double[] center = Geohash.center(cell);
		assertThat(Geohash.encode(center[0], center[1], Geohash.CELL_PRECISION)).isEqualTo(cell);
	}

	@Test
	void precisionSixCellIsAboutOneKmByHalfAKmInBengaluru() {
		// Precision 6 = 15 longitude bits and 15 latitude bits.
		GeoCell origin = GeoCell.snap(12.9352, 77.6245);
		double widthKm = GeoMath.haversineKm(0, 0, 0, 360.0 / Math.pow(2, 15)) * Math.cos(Math.toRadians(12.9352));
		double heightKm = GeoMath.haversineKm(0, 0, 180.0 / Math.pow(2, 15), 0);
		assertThat(widthKm).isCloseTo(1.19, within(0.05));
		assertThat(heightKm).isCloseTo(0.61, within(0.05));
		assertThat(origin.cell()).hasSize(6);
	}

	@Test
	void snappingDiscardsTheRawCoordinate() {
		GeoCell a = GeoCell.snap(12.93521, 77.62451);
		GeoCell b = GeoCell.snap(12.93530, 77.62460);
		assertThat(a).isEqualTo(b);
		assertThat(a.lat()).isNotEqualTo(12.93521);
	}
}
