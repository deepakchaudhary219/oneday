package oneday.grievance;

import java.util.List;

import oneday.grievance.GrievanceService.GrievanceView;
import oneday.grievance.GrievanceService.OfficerContact;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/grievances")
public class GrievanceController {

	private final GrievanceService grievances;

	public GrievanceController(GrievanceService grievances) {
		this.grievances = grievances;
	}

	/** Public: who the Grievance Officer is and how to reach them without the app. */
	@GetMapping("/officer")
	OfficerContact officer() {
		return grievances.officer();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	GrievanceView file(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FileRequest request) {
		return grievances.file(jwt.getSubject(), request.category(), request.subjectRef(), request.description());
	}

	@GetMapping
	List<GrievanceView> mine(@AuthenticationPrincipal Jwt jwt) {
		return grievances.mine(jwt.getSubject());
	}

	/**
	 * @param subjectRef what it is about, if anything specific: a moment id, a report id, or a link
	 */
	record FileRequest(@NotNull GrievanceCategory category, @Size(max = 64) String subjectRef,
			@NotBlank @Size(max = 2000) String description) {
	}
}
