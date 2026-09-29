package oneday.geo;

/** Coarse 8-point direction between cell centres; NONE when withheld or when both share a cell. */
public enum Direction {

	N, NE, E, SE, S, SW, W, NW, NONE;

	private static final Direction[] SECTORS = { N, NE, E, SE, S, SW, W, NW };

	static Direction fromBearing(double degrees) {
		return SECTORS[(int) Math.round(degrees / 45.0) % 8];
	}
}
