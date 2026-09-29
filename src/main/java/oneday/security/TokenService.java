package oneday.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import oneday.config.OneDayProperties;
import oneday.identity.AccountStatus;
import oneday.identity.User;
import oneday.staff.StaffDirectory;
import oneday.staff.StaffRole;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	/** JWT claim naming the session a token belongs to. */
	public static final String SESSION_CLAIM = "sid";

	private final JwtEncoder encoder;

	private final StaffDirectory staff;

	private final Clock clock;

	private final Duration ttl;

	public TokenService(JwtEncoder encoder, StaffDirectory staff, Clock clock, OneDayProperties properties) {
		this.encoder = encoder;
		this.staff = staff;
		this.clock = clock;
		this.ttl = properties.security().tokenTtl();
	}

	/**
	 * A short-lived access token bound to a session ({@code sid}): ending the session cuts it off at once.
	 * {@code refreshToken} is set only when a session starts or rotates; otherwise the client keeps its own.
	 */
	IssuedToken issue(User user, String sessionId, String refreshToken) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(SecurityConfig.ISSUER)
			.subject(user.getId())
			.issuedAt(now)
			.expiresAt(now.plus(ttl))
			.claim(SESSION_CLAIM, sessionId)
			.claim("scope", String.join(" ", scopes(user)))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, "Bearer", ttl.toSeconds(), refreshToken, user.isVerified(),
				user.getAccountStatus());
	}

	/** Staff scopes are only ever granted to active, liveness-verified accounts. */
	private List<String> scopes(User user) {
		List<String> scopes = new ArrayList<>(List.of("member"));
		if (user.isVerified() && user.isActive()) {
			scopes.add("verified");
			staff.roleOf(user.getId()).ifPresent(role -> {
				scopes.add("moderator");
				if (role == StaffRole.ADMIN) {
					scopes.add("admin");
				}
			});
		}
		return scopes;
	}

	/**
	 * {@code accountStatus} lets the app show a suspended account its restricted view (data export, notices,
	 * appeals) instead of a broken feed.
	 */
	public record IssuedToken(String token, String tokenType, long expiresInSeconds, String refreshToken,
			boolean verified, AccountStatus accountStatus) {
	}
}
