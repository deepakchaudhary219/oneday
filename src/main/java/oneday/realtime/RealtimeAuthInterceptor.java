package oneday.realtime;

import java.security.Principal;
import java.time.Instant;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authenticates the socket with the same decoder as the HTTP API (signature, expiry, issuer and the live
 * sign-in session), and allows a client nothing but a subscription to its own event queue.
 */
@Component
class RealtimeAuthInterceptor implements ChannelInterceptor {

	private final JwtDecoder jwt;

	private final RealtimeSessions sessions;

	RealtimeAuthInterceptor(JwtDecoder jwt, RealtimeSessions sessions) {
		this.jwt = jwt;
		this.sessions = sessions;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (stomp == null || stomp.getCommand() == null) {
			return message;
		}
		StompCommand command = stomp.getCommand();
		switch (command) {
			case CONNECT -> {
				String header = stomp.getFirstNativeHeader("Authorization");
				if (header == null || !header.startsWith("Bearer ")) {
					throw new MessageDeliveryException("Authorization required");
				}
				try {
					Jwt token = jwt.decode(header.substring(7));
					String userId = token.getSubject();
					stomp.setUser(new UserPrincipal(userId));
					sessions.authenticated(stomp.getSessionId(), userId, token.getClaimAsString("sid"),
							token.getExpiresAt() == null ? Instant.MAX : token.getExpiresAt());
				}
				catch (JwtException ex) {
					throw new MessageDeliveryException("Invalid or expired token");
				}
			}
			case SUBSCRIBE -> {
				if (stomp.getUser() == null || !("/user" + RealtimeConfig.EVENTS).equals(stomp.getDestination())) {
					throw new MessageDeliveryException("Only /user/queue/events may be subscribed");
				}
			}
			case SEND -> throw new MessageDeliveryException("The realtime channel is receive-only; use the HTTP API");
			default -> {
			}
		}
		return message;
	}

	record UserPrincipal(String getName) implements Principal {
	}
}
