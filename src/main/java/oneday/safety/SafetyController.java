package oneday.safety;

import oneday.safety.SafetyService.ReportReceipt;
import oneday.safety.SafetyService.SafetyTarget;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/safety")
public class SafetyController {

	private final SafetyService safety;

	public SafetyController(SafetyService safety) {
		this.safety = safety;
	}

	@PostMapping("/blocks")
	ResponseEntity<Void> block(@AuthenticationPrincipal Jwt jwt, @RequestBody BlockRequest request) {
		safety.block(jwt.getSubject(), new SafetyTarget(request.momentId(), request.signalId(), request.connectionId()));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/reports")
	@ResponseStatus(HttpStatus.CREATED)
	ReportReceipt report(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReportRequest request) {
		return safety.report(jwt.getSubject(),
				new SafetyTarget(request.momentId(), request.signalId(), request.connectionId()), request.category(),
				request.details(), Boolean.TRUE.equals(request.alsoBlock()));
	}

	record BlockRequest(String momentId, String signalId, String connectionId) {
	}

	record ReportRequest(String momentId, String signalId, String connectionId, @NotNull ReportCategory category,
			@Size(max = 1000) String details, Boolean alsoBlock) {
	}
}
