package oneday.chat;

import java.time.Instant;
import java.util.List;

import oneday.chat.ChatService.BalanceView;
import oneday.chat.ChatService.MessageView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

	public ChatController(ChatService chat) {
		this.chat = chat;
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
		return chat.send(jwt.getSubject(), conversationId, request.body());
	}

	@GetMapping("/balance")
	BalanceView balance(@AuthenticationPrincipal Jwt jwt, @PathVariable String conversationId) {
		return chat.balance(jwt.getSubject(), conversationId);
	}

	record SendMessage(@NotBlank @Size(max = 2000) String body) {
	}
}
