package oneday.prompts;

import java.time.LocalDate;
import java.util.List;

import oneday.prompts.PromptService.ScheduledView;
import oneday.prompts.PromptService.TodayView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromptController {

	private final PromptService prompts;

	public PromptController(PromptService prompts) {
		this.prompts = prompts;
	}

	/** Today's Prompt. Answer it by posting a moment with {@code promptKey}. */
	@GetMapping("/prompts/today")
	TodayView today(@AuthenticationPrincipal Jwt jwt) {
		return prompts.today(jwt.getSubject());
	}

	@PostMapping("/staff/prompts")
	@ResponseStatus(HttpStatus.CREATED)
	ScheduledView schedule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ScheduleRequest request) {
		return prompts.schedule(jwt.getSubject(), request.date(), request.homeRegion(), request.text(),
				request.activityHint());
	}

	@GetMapping("/staff/prompts")
	List<ScheduledView> upcoming(@AuthenticationPrincipal Jwt jwt) {
		return prompts.upcoming(jwt.getSubject());
	}

	public record ScheduleRequest(@NotNull LocalDate date, @Size(max = 8) String homeRegion,
			@NotBlank @Size(max = 140) String text, @Size(max = 30) String activityHint) {
	}
}
