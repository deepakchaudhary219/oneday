package oneday.security;

/** Tells the other replicas that sessions ended, so their {@link SessionLivenessCache} drops them. */
public interface SessionEvictions {

	void sessionEnded(String sessionId);

	void userSessionsEnded(String userId);
}
