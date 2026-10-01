package oneday.identity;

/** Progressive verification (blueprint §24.1 / §41.4): browsing needs none, contact needs VERIFIED. */
public enum VerificationStatus {
	UNVERIFIED, MANUAL_REVIEW, VERIFIED, REJECTED,
	/** Verified once, but the periodic re-check (every 90 days, blueprint §21.4) is due. */
	EXPIRED
}
