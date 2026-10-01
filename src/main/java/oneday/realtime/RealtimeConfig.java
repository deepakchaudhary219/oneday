package oneday.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Real-time delivery over STOMP/WebSocket at {@code /ws}. The channel is <b>server-to-client only</b>: clients
 * authenticate on CONNECT with their access token and may subscribe to exactly one destination, their own
 * {@code /user/queue/events}. All writes still go through the HTTP API (with its validation, budgets and
 * idempotency); the socket only tells the app that something happened, so a dropped socket never loses data.
 */
@Configuration
@EnableWebSocketMessageBroker
public class RealtimeConfig implements WebSocketMessageBrokerConfigurer {

	public static final String EVENTS = "/queue/events";

	private final RealtimeAuthInterceptor auth;

	private final RealtimeSessions sessions;

	private final String[] allowedOrigins;

	public RealtimeConfig(RealtimeAuthInterceptor auth, RealtimeSessions sessions,
			@Value("${oneday.realtime.allowed-origins:*}") String[] allowedOrigins) {
		this.auth = auth;
		this.sessions = sessions;
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue").setHeartbeatValue(new long[] { 20_000, 20_000 })
			.setTaskScheduler(sessions.heartbeatScheduler());
		registry.setUserDestinationPrefix("/user");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(auth);
	}

	@Override
	public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
		registration.setMessageSizeLimit(16 * 1024).addDecoratorFactory(sessions::decorate);
	}
}
