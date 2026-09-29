package oneday.discovery;

/** Map scopes (blueprint v2 §6.1). Precision drops as reach grows: privacy scales inversely with reach. */
public enum DiscoveryScope {
	/** Within the user's radius dial; distance band + coarse direction. */
	RADIUS,
	/** Across the city; "in your city" only. */
	CITY,
	/** City-wide, people from the viewer's home region (Roots Circles). */
	ROOTS,
	/** City-wide, people who share a language with the viewer. */
	LANGUAGE
}
