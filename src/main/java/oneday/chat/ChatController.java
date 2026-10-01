package oneday.chat;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import oneday.chat.ChatService.BalanceView;
import oneday.chat.ChatService.MessageView;
import oneday.e2ee.E2eeService.Outgoing;
import oneday.safety.ReportCategory;
import oneday.safety.SafetyService.ReportReceipt;
import oneday.security.TokenService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
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
@RequestMapping("/conversations/{conversationId}")
public class ChatController {

	private final ChatService chat;

	private final FrankedReports frankedReports;

	public ChatController(ChatService chat, FrankedReports frankedReports) {
		this.chat = chat;
		this.frankedReports = frankedReports;
	}

	@GetMapping("/messages")
	List<MessageView> history(@AuthenticationPrincipal Jwt jwt, @PathVariable String conversationId,
			@RequestParam(required = false) Instant before, @RequestParam(defaultValue = "30") int limit) {
		return chat.history(jwt.getSubject(), conversationId, before, limit);
	}

	@PostMapping("/messages")
	@ResponseStatus(HttpStatus.CREATED)
	MessageView send(@AuthenticationPrincipal Jwt jwt, @PathVariable String conversationId,
			@Valid @RequestBody SendMessage request) {
		return chat.send(jwt.getSubject(), conversationId, request.body(), Boolean.TRUE.equals(request.sendAnyway()));
	}

	/** End-to-end encrypted send: one envelope per device; see {@link oneday.e2ee.E2eeService}. */
	@PostMapping("/encrypted")
	@ResponseStatus(HttpStatus.CREATED)
	MessageView sendEncrypted(@AuthenticationPrincipal Jwt jwt, @PathVariable String conversationId,
			@RequestBody EncryptedMessage request) {
		return chat.sendEncrypted(jwt.getSubject(), jwt.getClaimAsString(TokenService.SESSION_CLAIM), conversationId,
				request.senderDevice(), decode(request.commitment()),
				request.envelopes() == null ? List.of() : request.envelopes());
	}

	/** Reports an encrypted message by revealing it; the franking commitment proves it is what was sent. */
	@PostMapping("/messages/{messageId}/report")
	@ResponseStatus(HttpStatus.CREATED)
	ReportReceipt report(@AuthenticationPrincipal Jwt jwt, @PathVariable String conversationId,
			@PathVariable String messageId, @Valid @RequestBody FrankedReport request) {
		return frankedReports.report(jwt.getSubject(), conversationId, messageId, request.category(),
				request.plaintext(), decode(request.frankingKey()), request.details(),
				Boolean.TRUE.equals(request.alsoBlock()));
	}

	@GetMapping("/balance")
	BalanceView balance(@AuthenticationPrincipal Jwt jwt, @PathVariable String conversationId) {
		return chat.balance(jwt.getSubject(), conversationId);
	}

	record EncryptedMessage(int senderDevice, String commitment, List<Outgoing> envelopes) {
	}

	record FrankedReport(@NotNull ReportCategory category, @NotNull @Size(max = 4000) String plaintext,
			@NotBlank String frankingKey, @Size(max = 1000) String details, Boolean alsoBlock) {
	}

	private static byte[] decode(String base64) {
		try {
			return base64 == null ? null : Base64.getDecoder().decode(base64);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** {@code sendAnyway}: the sender saw the Empathy Mirror's reflection and still wants to send. */
	record SendMessage(@NotBlank @Size(max = 2000) String body, Boolean sendAnyway) {
	}
}
