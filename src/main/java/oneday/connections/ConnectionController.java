package oneday.connections;

import java.time.Instant;
import java.util.List;

import oneday.chat.ChatService;
import oneday.chat.Conversation;
import oneday.connections.ConnectionService.CoupleView;
import oneday.connections.ConnectionService.SparkView;
import oneday.profile.Profile;
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

	private final ConnectionWarmth warmth;

	public ConnectionController(ConnectionService connections, ChatService chat, ProfileService profiles,
			ConnectionWarmth warmth) {
		this.warmth = warmth;
		this.connections = connections;
		this.chat = chat;
		this.profiles = profiles;
	}

	@GetMapping
	public List<ConnectionView> list(@AuthenticationPrincipal Jwt jwt) {
		String me = jwt.getSubject();
		Profile mine = profiles.require(me);
		return connections.active(me).stream().map(c -> {
			String conversationId = chat.forConnection(c.getId()).map(Conversation::getId).orElse(null);
			Profile them = profiles.require(c.otherThan(me));
			return new ConnectionView(c.getId(), conversationId, them.getDisplayName(), c.getOrigin(), c.getCreatedAt(),
					c.isMutualSpark(), c.isCouple(), warmth.of(c, conversationId, mine, them));
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

	@PostMapping("/{connectionId}/couple")
	public CoupleView couple(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		return connections.couple(jwt.getSubject(), connectionId, true);
	}

	@DeleteMapping("/{connectionId}/couple")
	public CoupleView leaveCouple(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		return connections.couple(jwt.getSubject(), connectionId, false);
	}

	/** Soft exit: silent for the other person. */
	@PostMapping("/{connectionId}/exit")
	public ResponseEntity<Void> exit(@AuthenticationPrincipal Jwt jwt, @PathVariable String connectionId) {
		connections.exit(jwt.getSubject(), connectionId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * {@code mutualSpark} is true only when both sides sparked, {@code coupleMode} only when both confirmed; a
	 * one-sided spark or confirmation is never exposed.
	 */
	public record ConnectionView(String id, String conversationId, String displayName, ConnectionOrigin origin,
			Instant since, boolean mutualSpark, boolean coupleMode, ConnectionWarmth.Warmth warmth) {
	}
}
