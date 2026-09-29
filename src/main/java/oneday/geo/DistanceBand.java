package oneday.geo;

/** What a viewer may learn about distance (blueprint v2 §6.2, the Precision Ladder). */
public enum DistanceBand {

	UNDER_1_KM("under 1 km"), KM_1_TO_5("1–5 km"), KM_5_TO_15("5–15 km"), IN_CITY("in your city"),
	NEARBY_AREA("nearby area");

	private final String label;

	DistanceBand(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
