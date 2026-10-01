package oneday.security;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Whether a sign-in session is still live, for long-lived connections that outlast a single request. */
@Component
public class SessionLiveness {

	private final SessionRepository sessions;

	private final Clock clock;

	SessionLiveness(SessionRepository sessions, Clock clock) {
		this.sessions = sessions;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public boolean isLive(String sessionId) {
		return sessions.existsByIdAndEndedAtIsNullAndExpiresAtAfter(sessionId, clock.instant());
	}
}
