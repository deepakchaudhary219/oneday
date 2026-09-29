package oneday.geo;

/** A snapped location: the geohash cell and its centre. Raw coordinates never leave {@link #snap}. */
public record GeoCell(String cell, double lat, double lon) {

	public static GeoCell snap(double rawLat, double rawLon) {
		return of(Geohash.encode(rawLat, rawLon, Geohash.CELL_PRECISION));
	}

	public static GeoCell of(String cell) {
		double[] center = Geohash.center(cell);
		return new GeoCell(cell, center[0], center[1]);
	}

	public String area() {
		return cell.substring(0, Math.min(cell.length(), Geohash.AREA_PRECISION));
	}

	public double distanceKm(GeoCell other) {
		return GeoMath.haversineKm(lat, lon, other.lat, other.lon);
	}
}
