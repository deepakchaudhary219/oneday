package oneday.geo;

/**
 * Minimal geohash codec. Precision 6 (~1.2 km x 0.6 km, ~0.73 km² at Bengaluru's latitude) is the
 * storage and privacy floor; precision 5 (~4.9 km square) is used for Safe Zones and the Heat layer.
 */
public final class Geohash {

	public static final int CELL_PRECISION = 6;

	public static final int AREA_PRECISION = 5;

	private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

	private Geohash() {
	}

	public static String encode(double lat, double lon, int precision) {
		double minLat = -90, maxLat = 90, minLon = -180, maxLon = 180;
		StringBuilder hash = new StringBuilder(precision);
		boolean evenBit = true;
		int bit = 0;
		int ch = 0;
		while (hash.length() < precision) {
			if (evenBit) {
				double mid = (minLon + maxLon) / 2;
				if (lon >= mid) {
					ch |= 1 << (4 - bit);
					minLon = mid;
				}
				else {
					maxLon = mid;
				}
			}
			else {
				double mid = (minLat + maxLat) / 2;
				if (lat >= mid) {
					ch |= 1 << (4 - bit);
					minLat = mid;
				}
				else {
					maxLat = mid;
				}
			}
			evenBit = !evenBit;
			if (bit < 4) {
				bit++;
			}
			else {
				hash.append(BASE32.charAt(ch));
				bit = 0;
				ch = 0;
			}
		}
		return hash.toString();
	}

	/** Centre of the cell as {lat, lon}. */
	public static double[] center(String hash) {
		double minLat = -90, maxLat = 90, minLon = -180, maxLon = 180;
		boolean evenBit = true;
		for (char c : hash.toCharArray()) {
			int value = BASE32.indexOf(c);
			if (value < 0) {
				throw new IllegalArgumentException("Invalid geohash: " + hash);
			}
			for (int mask = 16; mask > 0; mask >>= 1) {
				if (evenBit) {
					double mid = (minLon + maxLon) / 2;
					if ((value & mask) != 0) {
						minLon = mid;
					}
					else {
						maxLon = mid;
					}
				}
				else {
					double mid = (minLat + maxLat) / 2;
					if ((value & mask) != 0) {
						minLat = mid;
					}
					else {
						maxLat = mid;
					}
				}
				evenBit = !evenBit;
			}
		}
		return new double[] { (minLat + maxLat) / 2, (minLon + maxLon) / 2 };
	}
}
