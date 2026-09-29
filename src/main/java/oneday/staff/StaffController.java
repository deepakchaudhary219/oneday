package oneday.staff;

import java.util.List;

import oneday.identity.AccountAdministration.VerificationDecision;
import oneday.staff.StaffConsoleService.AuditItem;
import oneday.staff.StaffConsoleService.PendingErasure;
import oneday.staff.StaffConsoleService.ReportAction;
import oneday.staff.StaffConsoleService.ReportItem;
import oneday.staff.StaffConsoleService.ReviewItem;
import oneday.staff.StaffConsoleService.StaffMemberView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
import org.springframework.web.bind.annotation.RestController;

/** Trust & Safety console API. Moderator scope for queues; admin scope for accounts, staff and audit. */
@RestController
@RequestMapping("/staff")
public class StaffController {

	private final StaffConsoleService console;

	public StaffController(StaffConsoleService console) {
		this.console = console;
	}

	@GetMapping("/verification-queue")
	List<ReviewItem> verificationQueue(@AuthenticationPrincipal Jwt jwt) {
		return console.verificationQueue(jwt.getSubject());
	}

	@PostMapping("/verification/{userId}/decision")
	ReviewItem decide(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId,
			@Valid @RequestBody VerificationDecisionRequest request) {
		return console.decideVerification(jwt.getSubject(), userId, request.decision(), request.note());
	}

	@GetMapping("/reports")
	List<ReportItem> reports(@AuthenticationPrincipal Jwt jwt) {
		return console.reportQueue(jwt.getSubject());
	}

	@PostMapping("/reports/{reportId}/claim")
	ReportItem claim(@AuthenticationPrincipal Jwt jwt, @PathVariable String reportId) {
		return console.claim(jwt.getSubject(), reportId);
	}

	@PostMapping("/reports/{reportId}/resolve")
	ReportItem resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable String reportId,
			@Valid @RequestBody ResolveReportRequest request) {
		return console.resolve(jwt.getSubject(), reportId, request.action(), request.note());
	}

	@GetMapping("/erasures")
	List<PendingErasure> pendingErasures(@AuthenticationPrincipal Jwt jwt) {
		return console.pendingErasures(jwt.getSubject());
	}

	@PostMapping("/accounts/{userId}/reinstate")
	ResponseEntity<Void> reinstate(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId,
			@Valid @RequestBody(required = false) NoteRequest request) {
		console.reinstate(jwt.getSubject(), userId, request == null ? null : request.note());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/members")
	List<StaffMemberView> members(@AuthenticationPrincipal Jwt jwt) {
		return console.members(jwt.getSubject());
	}

	@PutMapping("/members/{userId}")
	StaffMemberView grant(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId,
			@Valid @RequestBody GrantRequest request) {
		return console.grant(jwt.getSubject(), userId, request.role());
	}

	@DeleteMapping("/members/{userId}")
	ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
		console.revoke(jwt.getSubject(), userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/audit")
	List<AuditItem> audit(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "50") int limit) {
		return console.audit(jwt.getSubject(), limit);
	}

	record VerificationDecisionRequest(@NotNull VerificationDecision decision, @Size(max = 500) String note) {
	}

	record ResolveReportRequest(@NotNull ReportAction action, @Size(max = 500) String note) {
	}

	record NoteRequest(@Size(max = 500) String note) {
	}

	record GrantRequest(@NotNull StaffRole role) {
	}
}
