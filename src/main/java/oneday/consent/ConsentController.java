package oneday.consent;

import java.util.List;

import oneday.consent.ConsentService.HistoryItem;
import oneday.consent.ConsentService.PurposeView;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Privacy settings: one switch per purpose, with what it processes and what switching it off does. */
@RestController
@RequestMapping("/consents")
public class ConsentController {

	private final ConsentService consents;

	public ConsentController(ConsentService consents) {
		this.consents = consents;
	}

	@GetMapping
	List<PurposeView> purposes(@AuthenticationPrincipal Jwt jwt) {
		return consents.purposes(jwt.getSubject());
	}

	@PostMapping("/{purpose}")
	List<PurposeView> grant(@AuthenticationPrincipal Jwt jwt, @PathVariable ConsentPurpose purpose) {
		return consents.grant(jwt.getSubject(), purpose);
	}

	@DeleteMapping("/{purpose}")
	List<PurposeView> withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable ConsentPurpose purpose) {
		return consents.withdraw(jwt.getSubject(), purpose);
	}

	@GetMapping("/history")
	List<HistoryItem> history(@AuthenticationPrincipal Jwt jwt) {
		return consents.history(jwt.getSubject());
	}
}
