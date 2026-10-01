package oneday.security;

import java.time.Clock;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * An access token is only as good as its session: signing out, reuse detection, suspension and erasure take
 * effect on the next request rather than when the token would have expired. One primary-key lookup per
 * request, cached briefly by {@link SessionLivenessCache}; the client answers the 401 by refreshing, and a failed refresh sends it to sign-in.
 */
final class SessionValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error SESSION_ENDED = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"The session has ended", null);

	private final SessionRepository sessions;

	private final Clock clock;

	private final SessionLivenessCache cache;

	SessionValidator(SessionRepository sessions, Clock clock, SessionLivenessCache cache) {
		this.sessions = sessions;
		this.clock = clock;
		this.cache = cache;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		String sessionId = jwt.getClaimAsString(TokenService.SESSION_CLAIM);
		if (sessionId == null) {
			return OAuth2TokenValidatorResult.failure(SESSION_ENDED);
		}
		if (cache.isKnownLive(sessionId)) {
			return OAuth2TokenValidatorResult.success();
		}
		if (sessions.existsByIdAndEndedAtIsNullAndExpiresAtAfter(sessionId, clock.instant())) {
			cache.rememberLive(sessionId, jwt.getSubject());
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(SESSION_ENDED);
	}
}
