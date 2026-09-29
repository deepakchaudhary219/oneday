package oneday.signals;

import java.util.List;

import oneday.signals.SignalService.DigestView;
import oneday.signals.SignalService.RevealView;
import oneday.signals.SignalService.SentSignalView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/signals")
public class SignalController {

	private final SignalService signals;

	public SignalController(SignalService signals) {
		this.signals = signals;
	}

	/** No free text by design: a reaction plus, optionally, one of the recipient's own activities. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SentSignalView send(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SendSignal request) {
		return signals.send(jwt.getSubject(), request.momentId(), request.reaction(), request.activityRef());
	}

	@GetMapping("/digest")
	DigestView digest(@AuthenticationPrincipal Jwt jwt) {
		return signals.digest(jwt.getSubject());
	}

	@GetMapping("/sent")
	List<SentSignalView> sent(@AuthenticationPrincipal Jwt jwt) {
		return signals.sentBy(jwt.getSubject());
	}

	@PostMapping("/{signalId}/reveal")
	RevealView reveal(@AuthenticationPrincipal Jwt jwt, @PathVariable String signalId) {
		return signals.reveal(jwt.getSubject(), signalId);
	}

	@PostMapping("/{signalId}/pass")
	ResponseEntity<Void> pass(@AuthenticationPrincipal Jwt jwt, @PathVariable String signalId) {
		signals.pass(jwt.getSubject(), signalId);
		return ResponseEntity.noContent().build();
	}

	record SendSignal(@NotBlank String momentId, @NotNull Reaction reaction, @Size(max = 30) String activityRef) {
	}
}
