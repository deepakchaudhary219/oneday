package oneday.connections;

public enum ConnectionOrigin {
	/** A stranger became a Connection through Signal → Mutual Reveal. */
	MUTUAL_REVEAL,
	/** Imported from the user's existing contacts (Friend Mode). */
	CONTACT_IMPORT
}
