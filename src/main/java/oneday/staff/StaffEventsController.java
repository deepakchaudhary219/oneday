package oneday.staff;

import java.util.List;

import oneday.events.EventOperations;
import oneday.events.EventOperations.DeadEvent;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin view of the outbox's dead-letter queue: inspect events that exhausted their retries, then requeue. */
@RestController
@RequestMapping("/staff/events")
public class StaffEventsController {

	private final EventOperations events;

	private final StaffDirectory staff;

	private final StaffAudit audit;

	public StaffEventsController(EventOperations events, StaffDirectory staff, StaffAudit audit) {
		this.events = events;
		this.staff = staff;
		this.audit = audit;
	}

	@GetMapping("/dead")
	List<DeadEvent> dead(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "50") int limit) {
		staff.require(jwt.getSubject(), StaffRole.ADMIN);
		return events.deadLetters(limit);
	}

	@PostMapping("/{eventId}/requeue")
	DeadEvent requeue(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
		staff.require(jwt.getSubject(), StaffRole.ADMIN);
		DeadEvent event = events.requeue(eventId);
		audit.record(jwt.getSubject(), "EVENT_REQUEUED", "EVENT", eventId, event.type());
		return event;
	}
}
