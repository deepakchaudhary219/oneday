package oneday.safety;

import java.util.Map;

import oneday.common.ApiException;
import oneday.safety.SafetyService.ReportReceipt;

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

	/** Exactly one target field: momentId, signalId, connectionId, planId, roomMessageId, rightNowId or dateId. */
	@PostMapping("/blocks")
	ResponseEntity<Void> block(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
		safety.block(jwt.getSubject(), safety.targetFrom(body));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/reports")
	@ResponseStatus(HttpStatus.CREATED)
	ReportReceipt report(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
		ReportCategory category;
		try {
			category = ReportCategory.valueOf(String.valueOf(body.get("category")));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.badRequest("CATEGORY_REQUIRED", "Choose what happened (category)");
		}
		Object details = body.get("details");
		if (details != null && (!(details instanceof String text) || text.length() > 1000)) {
			throw ApiException.badRequest("INVALID_DETAILS", "Details are text, up to 1000 characters");
		}
		return safety.report(jwt.getSubject(), safety.targetFrom(body), category, (String) details,
				Boolean.TRUE.equals(body.get("alsoBlock")));
	}
}
