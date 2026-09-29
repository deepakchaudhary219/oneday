package oneday.geo;

public final class GeoMath {

	private static final double EARTH_RADIUS_KM = 6371.0088;

	private GeoMath() {
	}

	public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
	}

	/** Initial bearing in degrees [0, 360). */
	public static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
		double phi1 = Math.toRadians(lat1);
		double phi2 = Math.toRadians(lat2);
		double dLon = Math.toRadians(lon2 - lon1);
		double y = Math.sin(dLon) * Math.cos(phi2);
		double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
		return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
	}

	/** Latitude/longitude half-extents (degrees) of a box enclosing a circle of {@code radiusKm}. */
	public static double[] boundingBoxDegrees(double lat, double radiusKm) {
		double dLat = radiusKm / 110.574;
		double dLon = radiusKm / (111.320 * Math.max(0.01, Math.cos(Math.toRadians(lat))));
		return new double[] { dLat, dLon };
	}
}
