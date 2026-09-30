package oneday.dates;

import java.time.Instant;
import java.util.List;

import oneday.dates.MeetingPointService.MeetingPointView;
import oneday.staff.StaffAudit;
import oneday.staff.StaffDirectory;
import oneday.staff.StaffRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trust &amp; Safety's Date Mode desk: open help requests (with the person's last shared position, which is
 * exactly what a responder needs) and Meeting Point curation. Every read of the alert queue is audited.
 */
@RestController
@RequestMapping("/staff")
public class DateSafetyDeskController {

	private final DateService dates;

	private final MeetingPointService meetingPoints;

	private final StaffDirectory staff;

	private final StaffAudit audit;

	public DateSafetyDeskController(DateService dates, MeetingPointService meetingPoints, StaffDirectory staff,
			StaffAudit audit) {
		this.dates = dates;
		this.meetingPoints = meetingPoints;
		this.staff = staff;
		this.audit = audit;
	}

	@GetMapping("/date-alerts")
	List<AlertItem> alerts(@AuthenticationPrincipal Jwt jwt) {
		staff.require(jwt.getSubject(), StaffRole.MODERATOR);
		audit.record(jwt.getSubject(), "DATE_ALERTS_VIEWED", "QUEUE", "date-alerts", null);
		return dates.openEscalations().stream().map(p -> {
			DatePlan plan = dates.find(p.getDateId()).orElse(null);
			return new AlertItem(p.getDateId(), p.getUserId(), plan == null ? null : plan.otherThan(p.getUserId()),
					p.getEscalationReason(), p.getEscalatedAt(), plan == null ? null : plan.getPlaceName(),
					plan == null ? null : plan.getStartsAt(), plan == null ? null : plan.getEndsAt(),
					p.getContactName() != null, p.getLat(), p.getLon(), p.getLocationAt(), p.getCheckedInAt(),
					p.getHomeSafeAt());
		}).toList();
	}

	@PostMapping("/date-alerts/{dateId}/{userId}/resolve")
	ResponseEntity<Void> resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable String dateId,
			@PathVariable String userId, @Valid @RequestBody ResolveRequest request) {
		staff.require(jwt.getSubject(), StaffRole.MODERATOR);
		dates.resolveEscalation(dateId, userId, jwt.getSubject());
		audit.record(jwt.getSubject(), "DATE_ALERT_RESOLVED", "DATE", dateId, request.note());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/meeting-points")
	@ResponseStatus(HttpStatus.CREATED)
	MeetingPointView createMeetingPoint(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody MeetingPointRequest request) {
		return meetingPoints.create(jwt.getSubject(), request.name(), request.category(), request.address(),
				request.lat(), request.lon(), request.safetyNotes());
	}

	@DeleteMapping("/meeting-points/{id}")
	ResponseEntity<Void> removeMeetingPoint(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
		meetingPoints.deactivate(jwt.getSubject(), id, null);
		return ResponseEntity.noContent().build();
	}

	public record AlertItem(String dateId, String userId, String otherUserId, EscalationReason reason,
			Instant raisedAt, String placeName, Instant startsAt, Instant endsAt, boolean trustedContactSet,
			Double lastLat, Double lastLon, Instant lastLocationAt, Instant checkedInAt, Instant homeSafeAt) {
	}

	public record ResolveRequest(@NotBlank @Size(max = 500) String note) {
	}

	public record MeetingPointRequest(@NotBlank @Size(max = 120) String name, @NotNull MeetingPointCategory category,
			@NotBlank @Size(max = 300) String address, @DecimalMin("-90") @DecimalMax("90") double lat,
			@DecimalMin("-180") @DecimalMax("180") double lon, @Size(max = 300) String safetyNotes) {
	}
}
