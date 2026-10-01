package oneday.plans;

import java.time.Instant;
import java.util.List;

import oneday.plans.PlanService.JoinRequest;
import oneday.plans.PlanService.PlanCard;
import oneday.plans.PlanService.PlanView;
import oneday.plans.PlanService.RoomMessage;
import oneday.platform.ReplicaReads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans")
public class PlanController {

	private final PlanService plans;

	public PlanController(PlanService plans) {
		this.plans = plans;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PlanView host(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HostRequest request) {
		return plans.host(jwt.getSubject(), request.meetingPointId(), request.activity(), request.title(),
				Boolean.TRUE.equals(request.rootsOnly()), request.capacity(), request.startsAt(), request.endsAt());
	}

	@GetMapping
	List<PlanCard> nearby(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String activity,
			@RequestParam(defaultValue = "10") int radiusKm) {
		return ReplicaReads.run(() -> plans.nearby(jwt.getSubject(), activity, radiusKm));
	}

	@GetMapping("/mine")
	List<PlanView> mine(@AuthenticationPrincipal Jwt jwt) {
		return plans.mine(jwt.getSubject());
	}

	@GetMapping("/{planId}")
	PlanView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId) {
		return plans.get(jwt.getSubject(), planId);
	}

	@PostMapping("/{planId}/join")
	PlanView join(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId) {
		return plans.requestToJoin(jwt.getSubject(), planId);
	}

	@PostMapping("/{planId}/leave")
	ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId) {
		plans.leave(jwt.getSubject(), planId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{planId}/requests")
	List<JoinRequest> requests(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId) {
		return plans.requests(jwt.getSubject(), planId);
	}

	@PostMapping("/{planId}/requests/{handle}/approve")
	PlanView approve(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId, @PathVariable String handle) {
		return plans.decide(jwt.getSubject(), planId, handle, true);
	}

	@PostMapping("/{planId}/requests/{handle}/decline")
	PlanView decline(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId, @PathVariable String handle) {
		return plans.decide(jwt.getSubject(), planId, handle, false);
	}

	@GetMapping("/{planId}/room")
	List<RoomMessage> room(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
			@RequestParam(required = false) Instant before, @RequestParam(defaultValue = "30") int limit) {
		return plans.room(jwt.getSubject(), planId, before, limit);
	}

	@PostMapping("/{planId}/room")
	@ResponseStatus(HttpStatus.CREATED)
	RoomMessage say(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
			@Valid @RequestBody SayRequest request) {
		return plans.say(jwt.getSubject(), planId, request.body());
	}

	public record HostRequest(@NotBlank String meetingPointId, @NotBlank @Size(max = 30) String activity,
			@Size(max = 80) String title, Boolean rootsOnly, int capacity, @NotNull Instant startsAt,
			@NotNull Instant endsAt) {
	}

	public record SayRequest(@NotBlank @Size(max = 1000) String body) {
	}
}
