package oneday.identity;

public enum AccountStatus {
	ACTIVE, SUSPENDED,
	/**
	 * The holder asked for erasure while under a safety hold. To them and to everyone else the account is
	 * gone (identifiers released, invisible, no login), but evidence is kept until the hold lifts.
	 */
	DEACTIVATED
}
