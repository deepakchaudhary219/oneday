package oneday.attestation;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.common.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Applies device attestation where spoofing matters most: signup and location updates. Rolled out in three
 * modes ({@code oneday.attestation.mode}): {@code off}; {@code monitor} (verify what the app sends and record
 * the verdict as a metric, never block: measure false positives first); {@code enforce} (missing or failed
 * attestation is refused). If the vendor is unreachable in enforce mode the request is refused too: fail
 * closed, because a spoofer can always make the check "unreachable".
 */
@Component
public class AttestationGuard {

	public static final String HEADER = "X-Device-Integrity";

	private static final Logger log = LoggerFactory.getLogger(AttestationGuard.class);

	public enum Mode {
		OFF, MONITOR, ENFORCE
	}

	private final ObjectProvider<DeviceAttestor> attestor;

	private final MeterRegistry metrics;

	private final Mode mode;

	public AttestationGuard(ObjectProvider<DeviceAttestor> attestor, MeterRegistry metrics,
			@Value("${oneday.attestation.mode:off}") String mode) {
		this.attestor = attestor;
		this.metrics = metrics;
		this.mode = Mode.valueOf(mode.trim().toUpperCase());
		if (this.mode == Mode.ENFORCE && attestor.getIfAvailable() == null) {
			throw new IllegalStateException("oneday.attestation.mode=enforce needs oneday.attestation.provider");
		}
	}

	/** @param action what the token must have been minted for: {@code signup} or {@code location} */
	public void check(String token, String action) {
		if (mode == Mode.OFF) {
			return;
		}
		DeviceAttestor provider = attestor.getIfAvailable();
		String outcome;
		if (token == null || token.isBlank() || provider == null) {
			outcome = "missing";
		}
		else {
			try {
				outcome = provider.verify(token.trim(), action) == DeviceAttestor.Verdict.TRUSTED ? "trusted" : "failed";
			}
			catch (RuntimeException ex) {
				log.warn("Device attestation unavailable", ex);
				outcome = "unavailable";
			}
		}
		metrics.counter("oneday.attestation", "action", action, "outcome", outcome).increment();
		if (mode == Mode.ENFORCE && !"trusted".equals(outcome)) {
			throw ApiException.forbidden("DEVICE_NOT_TRUSTED",
					"Please use the official OneDay app on a device that passes the platform's integrity checks");
		}
	}
}
