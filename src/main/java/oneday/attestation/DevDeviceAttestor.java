package oneday.attestation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local development and tests: {@code dev-ok} passes, anything else fails. */
@Component
@ConditionalOnProperty(name = "oneday.attestation.provider", havingValue = "dev")
class DevDeviceAttestor implements DeviceAttestor {

	@Override
	public Verdict verify(String token, String action) {
		return "dev-ok".equals(token) ? Verdict.TRUSTED : Verdict.FAILED;
	}
}
