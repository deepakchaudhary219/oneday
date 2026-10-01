package oneday.calls;

import oneday.calls.CallService.CallView;
import oneday.calls.CallService.SignalKind;
import oneday.calls.TurnCredentials.IceServers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CallController {

	private final CallService calls;

	private final TurnCredentials turn;

	public CallController(CallService calls, TurnCredentials turn) {
		this.calls = calls;
		this.turn = turn;
	}

	@PostMapping("/connections/{connectionId}/calls")
	@ResponseStatus(HttpStatus.CREATED)
	CallView start(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		return calls.start(jwt.getSubject(), connectionId);
	}

	@GetMapping("/calls/{callId}")
	CallView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String callId) {
		return calls.get(jwt.getSubject(), callId);
	}

	@PostMapping("/calls/{callId}/accept")
	CallView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String callId) {
		return calls.accept(jwt.getSubject(), callId);
	}

	@PostMapping("/calls/{callId}/decline")
	CallView decline(@AuthenticationPrincipal Jwt jwt, @PathVariable String callId) {
		return calls.decline(jwt.getSubject(), callId);
	}

	@PostMapping("/calls/{callId}/end")
	CallView end(@AuthenticationPrincipal Jwt jwt, @PathVariable String callId) {
		return calls.hangUp(jwt.getSubject(), callId);
	}

	@PutMapping("/calls/{callId}/layer")
	CallView layer(@AuthenticationPrincipal Jwt jwt, @PathVariable String callId, @RequestBody LayerRequest request) {
		return calls.setLayer(jwt.getSubject(), callId, request.wants());
	}

	@PostMapping("/calls/{callId}/signal")
	ResponseEntity<Void> signal(@AuthenticationPrincipal Jwt jwt, @PathVariable String callId,
			@RequestBody SignalRequest request) {
		calls.signal(jwt.getSubject(), callId, request.kind(), request.payload());
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/calls/ice-servers")
	IceServers iceServers(@AuthenticationPrincipal Jwt jwt) {
		return turn.forUser(jwt.getSubject());
	}

	record LayerRequest(Layer wants) {
	}

	/** {@code payload}: the SDP or ICE candidate JSON, relayed untouched. */
	record SignalRequest(SignalKind kind, String payload) {
	}
}
