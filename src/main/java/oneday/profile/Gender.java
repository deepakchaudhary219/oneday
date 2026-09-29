package oneday.profile;

/**
 * Optional and private (blueprint v2 §5.1): never displayed, never filterable; used only to lean
 * discovery when both people have the Dating Lens on and match each other's interest, and in aggregate
 * for the gender-balance density gate.
 */
public enum Gender {
	WOMAN, MAN, NON_BINARY
}
