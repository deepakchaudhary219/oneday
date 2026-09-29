package oneday.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import oneday.config.OneDayProperties;
import oneday.identity.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private final JwtEncoder encoder;

	private final Clock clock;

	private final Duration ttl;

	public TokenService(JwtEncoder encoder, Clock clock, OneDayProperties properties) {
		this.encoder = encoder;
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
			.claim("scope", user.isVerified() ? "member verified" : "member")
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, "Bearer", ttl.toSeconds(), user.isVerified());
	}

	public record IssuedToken(String token, String tokenType, long expiresInSeconds, boolean verified) {
	}
}
