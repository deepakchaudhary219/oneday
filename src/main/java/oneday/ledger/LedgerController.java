package oneday.ledger;

import oneday.platform.ReplicaReads;
import oneday.ledger.LedgerService.LedgerView;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ledger")
public class LedgerController {

	private final LedgerService ledger;

	private final WeeklyRecap recap;

	public LedgerController(LedgerService ledger, WeeklyRecap recap) {
		this.ledger = ledger;
		this.recap = recap;
	}

	/** Last week as a short private story: one highlight, a few facts, a kind ending. */
	@GetMapping("/week")
	public WeeklyRecap.RecapView lastWeek(@AuthenticationPrincipal Jwt jwt) {
		return ReplicaReads.run(() -> recap.lastWeek(jwt.getSubject()));
	}

	/** The caller's own month ({@code 2026-09}; defaults to the current month in their time zone). */
	@GetMapping
	public LedgerView month(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String month) {
		return ReplicaReads.run(() -> ledger.month(jwt.getSubject(), month));
	}
}
