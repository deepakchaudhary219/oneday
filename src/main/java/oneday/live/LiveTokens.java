package oneday.live;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Access tokens for a LiveKit-compatible SFU: HS256 JWTs with {@code iss} = API key, {@code sub} = an opaque
 * per-session identity (never a user id) and a {@code video} grant naming the room and what the holder may
 * do. No recording grants are ever issued. Viewer tokens are short-lived and refreshed by re-joining, so a
 * block or the end of the live cuts access within minutes.
 */
@Component
class LiveTokens {

	static final Duration HOST_TTL = Duration.ofMinutes(70);

	static final Duration VIEWER_TTL = Duration.ofMinutes(10);

	private final JwtEncoder encoder;

	private final String apiKey;

	private final String serverUrl;

	private final Clock clock;

	LiveTokens(@Value("${oneday.live.api-key:devkey}") String apiKey,
			@Value("${oneday.live.api-secret:dev-only-livekit-secret-0123456789abcdef}") String apiSecret,
			@Value("${oneday.live.server-url:ws://localhost:7880}") String serverUrl, Clock clock) {
		if (apiSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("oneday.live.api-secret must be at least 32 bytes");
		}
		this.encoder = new NimbusJwtEncoder(
				new ImmutableSecret<>(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		this.apiKey = apiKey;
		this.serverUrl = serverUrl;
		this.clock = clock;
	}

	Access issue(String room, String identity, String displayName, boolean host) {
		Instant now = clock.instant();
		Instant expires = now.plus(host ? HOST_TTL : VIEWER_TTL);
		Map<String, Object> video = new LinkedHashMap<>();
		video.put("room", room);
		video.put("roomJoin", true);
		video.put("canPublish", host);
		video.put("canPublishData", false);
		video.put("canSubscribe", true);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(apiKey)
			.subject(identity)
			.notBefore(now)
			.expiresAt(expires)
			.claim("name", displayName)
			.claim("video", video)
			.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return new Access(serverUrl, room, token, expires);
	}

	/** What the app hands to the LiveKit client SDK. */
	public record Access(String serverUrl, String room, String token, Instant expiresAt) {
	}
}
