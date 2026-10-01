package oneday.wellbeing;

import java.util.List;

import oneday.wellbeing.WellbeingService.AnswerView;
import oneday.wellbeing.WellbeingService.CheckView;
import oneday.wellbeing.WellbeingService.WeekView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WellbeingController {

	private final WellbeingService wellbeing;

	public WellbeingController(WellbeingService wellbeing) {
		this.wellbeing = wellbeing;
	}

	/** Called once at session start; the app shows the one-tap question only when {@code ask} is true. */
	@GetMapping("/wellbeing/check")
	CheckView check(@AuthenticationPrincipal Jwt jwt) {
		return wellbeing.check(jwt.getSubject());
	}

	@PostMapping("/wellbeing/answer")
	AnswerView answer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AnswerRequest request) {
		return wellbeing.answer(jwt.getSubject(), request.wellSpent());
	}

	/** Admin dashboard: Weekly Meaningful Actives and the well-spent share, newest week first. */
	@GetMapping("/staff/metrics/engagement")
	List<WeekView> engagement(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "8") int weeks) {
		return wellbeing.engagement(jwt.getSubject(), weeks);
	}

	public record AnswerRequest(@NotNull Boolean wellSpent) {
	}
}
