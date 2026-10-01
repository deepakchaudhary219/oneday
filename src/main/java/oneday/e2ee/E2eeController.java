package oneday.e2ee;

import java.util.List;
import java.util.Map;

import oneday.e2ee.E2eeService.Bundle;
import oneday.e2ee.E2eeService.DeviceView;
import oneday.e2ee.E2eeService.InboxItem;
import oneday.e2ee.E2eeService.NewDevice;
import oneday.e2ee.E2eeService.PreKey;
import oneday.e2ee.E2eeService.SignedPreKey;
import oneday.security.TokenService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Key directory and per-device inbox for end-to-end encrypted chat. Keys are base64. */
@RestController
@RequestMapping("/e2ee")
public class E2eeController {

	private final E2eeService e2ee;

	public E2eeController(E2eeService e2ee) {
		this.e2ee = e2ee;
	}

	@PostMapping("/devices")
	@ResponseStatus(HttpStatus.CREATED)
	DeviceView register(@AuthenticationPrincipal Jwt jwt, @RequestBody NewDevice request) {
		return e2ee.register(jwt.getSubject(), session(jwt), request);
	}

	@GetMapping("/devices")
	List<DeviceView> mine(@AuthenticationPrincipal Jwt jwt) {
		return e2ee.mine(jwt.getSubject());
	}

	@DeleteMapping("/devices/{deviceId}")
	ResponseEntity<Void> unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable int deviceId) {
		e2ee.unlink(jwt.getSubject(), deviceId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/devices/{deviceId}/signed-prekey")
	DeviceView rotate(@AuthenticationPrincipal Jwt jwt, @PathVariable int deviceId,
			@RequestBody SignedPreKey request) {
		return e2ee.rotateSignedPreKey(jwt.getSubject(), session(jwt), deviceId, request);
	}

	@PostMapping("/devices/{deviceId}/prekeys")
	DeviceView prekeys(@AuthenticationPrincipal Jwt jwt, @PathVariable int deviceId, @RequestBody PreKeys request) {
		return e2ee.uploadPreKeys(jwt.getSubject(), session(jwt), deviceId, request.oneTimePreKeys());
	}

	@GetMapping("/devices/{deviceId}/bundles")
	List<Bundle> ownBundles(@AuthenticationPrincipal Jwt jwt, @PathVariable int deviceId) {
		return e2ee.ownBundles(jwt.getSubject(), session(jwt), deviceId);
	}

	@GetMapping("/connections/{connectionId}/bundles")
	List<Bundle> bundles(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		return e2ee.bundlesFor(jwt.getSubject(), connectionId);
	}

	@GetMapping("/devices/{deviceId}/inbox")
	List<InboxItem> inbox(@AuthenticationPrincipal Jwt jwt, @PathVariable int deviceId,
			@RequestParam(defaultValue = "100") int limit) {
		return e2ee.inbox(jwt.getSubject(), session(jwt), deviceId, limit);
	}

	@PostMapping("/devices/{deviceId}/inbox/ack")
	Map<String, Integer> acknowledge(@AuthenticationPrincipal Jwt jwt, @PathVariable int deviceId,
			@RequestBody Ack request) {
		return Map.of("deleted", e2ee.acknowledge(jwt.getSubject(), session(jwt), deviceId, request.envelopeIds()));
	}

	private static String session(Jwt jwt) {
		return jwt.getClaimAsString(TokenService.SESSION_CLAIM);
	}

	record PreKeys(List<PreKey> oneTimePreKeys) {
	}

	record Ack(List<String> envelopeIds) {
	}
}
