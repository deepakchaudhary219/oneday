package oneday.identity;

/** Progressive verification (blueprint §24.1 / §41.4): browsing needs none, contact needs VERIFIED. */
public enum VerificationStatus {
	UNVERIFIED, MANUAL_REVIEW, VERIFIED, REJECTED
}
