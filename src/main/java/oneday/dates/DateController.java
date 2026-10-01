package oneday.dates;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import oneday.dates.DateService.CheckInView;
import oneday.dates.DateService.DateView;
import oneday.dates.DateService.DebriefView;
import oneday.dates.DateService.SharedPlanView;
import oneday.dates.DateService.TrustedContactView;
import oneday.dates.MeetingPointService.MeetingPointView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Date Mode API (blueprint v2 §5.3) plus the trusted contact's public page and Meeting Points. */
@RestController
public class DateController {

	private final DateService dates;

	private final MeetingPointService meetingPoints;

	public DateController(DateService dates, MeetingPointService meetingPoints) {
		this.dates = dates;
		this.meetingPoints = meetingPoints;
	}

	@PostMapping("/dates")
	@ResponseStatus(HttpStatus.CREATED)
	DateView propose(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProposeRequest request) {
		return dates.propose(jwt.getSubject(), request.connectionId(), request.meetingPointId(), request.placeName(),
				request.startsAt(), request.endsAt());
	}

	@GetMapping("/dates")
	List<DateView> list(@AuthenticationPrincipal Jwt jwt) {
		return dates.list(jwt.getSubject());
	}

	@GetMapping("/dates/{dateId}")
	DateView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId) {
		return dates.get(jwt.getSubject(), dateId);
	}

	@PostMapping("/dates/{dateId}/accept")
	DateView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId) {
		return dates.accept(jwt.getSubject(), dateId);
	}

	@PostMapping("/dates/{dateId}/decline")
	DateView decline(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId) {
		return dates.decline(jwt.getSubject(), dateId);
	}

	@PostMapping("/dates/{dateId}/cancel")
	DateView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId) {
		return dates.cancel(jwt.getSubject(), dateId);
	}

	/** End-of-date confirmation: "I'm home safe". */
	@PostMapping("/dates/{dateId}/end")
	DateView end(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId) {
		return dates.end(jwt.getSubject(), dateId);
	}

	@PutMapping("/dates/{dateId}/sharing")
	DateView sharing(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId,
			@Valid @RequestBody SharingRequest request) {
		return dates.setLocationSharing(jwt.getSubject(), dateId, request.enabled());
	}

	@PutMapping("/dates/{dateId}/location")
	DateView location(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId,
			@Valid @RequestBody PositionRequest request) {
		return dates.updateLocation(jwt.getSubject(), dateId, request.lat(), request.lon());
	}

	@PutMapping("/dates/{dateId}/trusted-contact")
	TrustedContactView trustedContact(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId,
			@Valid @RequestBody TrustedContactRequest request) {
		return dates.setTrustedContact(jwt.getSubject(), dateId, request.name(), request.phone());
	}

	@DeleteMapping("/dates/{dateId}/trusted-contact")
	ResponseEntity<Void> removeTrustedContact(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId) {
		dates.removeTrustedContact(jwt.getSubject(), dateId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/dates/{dateId}/check-in-time")
	DateView checkInTime(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId,
			@Valid @RequestBody CheckInTimeRequest request) {
		return dates.scheduleCheckIn(jwt.getSubject(), dateId, request.at());
	}

	/** Answers "Going OK?"; {@code needHelp: true} alerts the trusted contact and Trust &amp; Safety. */
	@PostMapping("/dates/{dateId}/check-in")
	CheckInView checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId,
			@RequestBody(required = false) CheckInRequest request) {
		return dates.checkIn(jwt.getSubject(), dateId, request != null && request.needHelp());
	}

	@PostMapping("/dates/{dateId}/sos")
	CheckInView sos(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId) {
		return dates.sos(jwt.getSubject(), dateId);
	}

	@PostMapping("/dates/{dateId}/debrief")
	DebriefView debrief(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId,
			@Valid @RequestBody DebriefRequest request) {
		return dates.debrief(jwt.getSubject(), dateId, request.answers());
	}

	@GetMapping("/dates/{dateId}/debrief")
	DebriefView debriefView(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId) {
		return dates.debriefView(jwt.getSubject(), dateId);
	}

	/** Public: the trusted contact has no account. The unguessable token is the only credential. */
	@GetMapping("/date-share/{token}")
	SharedPlanView shared(@PathVariable String token) {
		return dates.shared(token);
	}

	@GetMapping("/meeting-points")
	List<MeetingPointView> meetingPoints(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) MeetingPointCategory category,
			@RequestParam(defaultValue = "5") int radiusKm) {
		return meetingPoints.nearby(jwt.getSubject(), category, radiusKm);
	}

	public record ProposeRequest(@NotBlank String connectionId, String meetingPointId, @Size(max = 160) String placeName,
			@NotNull Instant startsAt, @NotNull Instant endsAt) {
	}

	public record SharingRequest(boolean enabled) {
	}

	public record PositionRequest(double lat, double lon) {
	}

	public record TrustedContactRequest(@Size(max = 60) String name, @NotBlank @Size(max = 24) String phone) {
	}

	public record CheckInTimeRequest(@NotNull Instant at) {
	}

	public record CheckInRequest(boolean needHelp) {
	}

	public record DebriefRequest(@NotNull Set<DebriefAnswer> answers) {
	}
}
