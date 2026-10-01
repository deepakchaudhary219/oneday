package oneday.plus;

import oneday.plus.PlusService.CheckoutView;
import oneday.plus.PlusService.PlusView;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlusController {

	private final PlusService plus;

	public PlusController(PlusService plus) {
		this.plus = plus;
	}

	@GetMapping("/plus")
	PlusView status(@AuthenticationPrincipal Jwt jwt) {
		return plus.status(jwt.getSubject());
	}

	@PostMapping("/plus/subscribe")
	CheckoutView subscribe(@AuthenticationPrincipal Jwt jwt) {
		return plus.subscribe(jwt.getSubject());
	}

	@PostMapping("/plus/cancel")
	PlusView cancel(@AuthenticationPrincipal Jwt jwt) {
		return plus.cancel(jwt.getSubject());
	}

	/** Razorpay webhooks (public; authenticated by the HMAC signature over the raw body). */
	@PostMapping("/webhooks/razorpay")
	ResponseEntity<Void> razorpay(@RequestBody String body,
			@RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
			@RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
		plus.webhook(body, signature, eventId);
		return ResponseEntity.ok().build();
	}
}
