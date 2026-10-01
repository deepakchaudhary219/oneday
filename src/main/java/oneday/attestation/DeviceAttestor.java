package oneday.attestation;

/**
 * Device attestation port (blueprint §47.6, tech arch v2 §5.3): does this request come from our genuine app on a
 * genuine device? It raises the cost of scripted signups and GPS spoofing.
 */
public interface DeviceAttestor {

	Verdict verify(String token, String action);

	/**
	 * Whether this attestor reads tokens of this shape. Attestors are consulted in {@code @Order}; platform
	 * specific ones (App Attest's {@code appattest.} prefix) come before the catch-all.
	 */
	default boolean handles(String token) {
		return true;
	}

	enum Verdict {
		/** Genuine app, genuine device. */
		TRUSTED,
		/** Checked and failed (tampered app, emulator, rooted device, stale or replayed token). */
		FAILED
	}
}
