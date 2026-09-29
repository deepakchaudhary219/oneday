package oneday.connections;

import java.time.Instant;
import java.util.List;

import oneday.chat.ChatService;
import oneday.chat.Conversation;
import oneday.connections.ConnectionService.SparkView;
import oneday.profile.ProfileService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/connections")
public class ConnectionController {

	private final ConnectionService connections;

	private final ChatService chat;

	private final ProfileService profiles;

	public ConnectionController(ConnectionService connections, ChatService chat, ProfileService profiles) {
		this.connections = connections;
		this.chat = chat;
		this.profiles = profiles;
	}

	@GetMapping
	public List<ConnectionView> list(@AuthenticationPrincipal Jwt jwt) {
		String me = jwt.getSubject();
		return connections.active(me).stream().map(c -> {
			String conversationId = chat.forConnection(c.getId()).map(Conversation::getId).orElse(null);
			String name = profiles.require(c.otherThan(me)).getDisplayName();
			return new ConnectionView(c.getId(), conversationId, name, c.getOrigin(), c.getCreatedAt(),
					c.isMutualSpark());
		}).toList();
	}

	@PostMapping("/{connectionId}/spark")
	public SparkView spark(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		return connections.spark(jwt.getSubject(), connectionId, true);
	}

	@DeleteMapping("/{connectionId}/spark")
	public SparkView withdrawSpark(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		return connections.spark(jwt.getSubject(), connectionId, false);
	}

	/** Soft exit: silent for the other person. */
	@PostMapping("/{connectionId}/exit")
	public ResponseEntity<Void> exit(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		connections.exit(jwt.getSubject(), connectionId);
		return ResponseEntity.noContent().build();
	}

	/** {@code mutualSpark} is true only when both sides sparked; a one-sided spark is never exposed. */
	public record ConnectionView(String id, String conversationId, String displayName, ConnectionOrigin origin,
			Instant since, boolean mutualSpark) {
	}
}
