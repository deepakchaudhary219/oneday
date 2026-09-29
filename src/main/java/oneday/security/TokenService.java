package oneday.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import oneday.config.OneDayProperties;
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

	public IssuedToken issue(User user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(SecurityConfig.ISSUER)
			.subject(user.getId())
			.issuedAt(now)
			.expiresAt(now.plus(ttl))
			.claim("scope", String.join(" ", scopes(user)))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, "Bearer", ttl.toSeconds(), user.isVerified());
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

	public record IssuedToken(String token, String tokenType, long expiresInSeconds, boolean verified) {
	}
}
