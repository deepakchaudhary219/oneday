package oneday.verification;

/**
 * Port to a liveness + age-estimation vendor (buy, don't build; see market doc §3.1). The client runs
 * the vendor SDK and sends us the resulting session token; the adapter validates it server-side.
 */
public interface LivenessVerifier {

	/** Stable provider id stored with each attempt, e.g. "dev" or a vendor name. */
	String provider();

	LivenessResult verify(String sessionToken);

	/**
	 * @param livePerson whether a real, present person completed the check
	 * @param estimatedAge ML age estimate (secondary signal, never a replacement for declared DOB)
	 * @param confidence vendor confidence in [0, 1]
	 */
	record LivenessResult(boolean livePerson, int estimatedAge, double confidence) {
	}
}
