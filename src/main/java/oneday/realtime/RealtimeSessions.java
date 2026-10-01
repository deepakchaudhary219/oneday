package oneday.realtime;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import oneday.security.SessionLiveness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * The sockets this replica holds. A socket lives no longer than the access token it connected with, and is
 * closed within {@code 30 s} of its sign-in session ending (sign-out, suspension, theft detection), so a
 * long-lived connection can't outlive the account's right to receive data. The app reconnects with a
 * refreshed token.
 */
@Component
public class RealtimeSessions {

	static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4001, "Token expired; reconnect with a fresh token");

	static final CloseStatus SESSION_ENDED = new CloseStatus(4003, "Session ended");

	private static final Logger log = LoggerFactory.getLogger(RealtimeSessions.class);

	private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();

	private final Map<String, Auth> auth = new ConcurrentHashMap<>();

	private final SessionLiveness liveness;

	private final Clock clock;

	private final ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();

	public RealtimeSessions(SessionLiveness liveness, Clock clock) {
		this.liveness = liveness;
		this.clock = clock;
		this.heartbeats.setPoolSize(1);
		this.heartbeats.setThreadNamePrefix("ws-heartbeat-");
		this.heartbeats.initialize();
	}

	TaskScheduler heartbeatScheduler() {
		return heartbeats;
	}

	WebSocketHandler decorate(WebSocketHandler handler) {
		return new WebSocketHandlerDecorator(handler) {

			@Override
			public void afterConnectionEstablished(WebSocketSession session) throws Exception {
				sockets.put(session.getId(), session);
				super.afterConnectionEstablished(session);
			}

			@Override
			public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
				sockets.remove(session.getId());
				auth.remove(session.getId());
				super.afterConnectionClosed(session, status);
			}
		};
	}

	void authenticated(String socketId, String userId, String signInSession, Instant expiresAt) {
		auth.put(socketId, new Auth(userId, signInSession, expiresAt));
	}

	/** Closes sockets whose token expired or whose sign-in session ended. */
	@Scheduled(fixedDelayString = "${oneday.realtime.sweep-interval:PT30S}")
	public int sweep() {
		Instant now = clock.instant();
		int closed = 0;
		for (Map.Entry<String, Auth> entry : auth.entrySet()) {
			Auth a = entry.getValue();
			CloseStatus reason = !now.isBefore(a.expiresAt()) ? TOKEN_EXPIRED
					: a.signInSession() == null || !liveness.isLive(a.signInSession()) ? SESSION_ENDED : null;
			if (reason != null) {
				close(entry.getKey(), reason);
				closed++;
			}
		}
		return closed;
	}

	public int connectedSockets() {
		return sockets.size();
	}

	private void close(String socketId, CloseStatus reason) {
		auth.remove(socketId);
		WebSocketSession socket = sockets.remove(socketId);
		if (socket != null) {
			try {
				socket.close(reason);
			}
			catch (IOException ex) {
				log.debug("Closing a socket failed", ex);
			}
		}
	}

	private record Auth(String userId, String signInSession, Instant expiresAt) {
	}
}
