package oneday.calls;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import oneday.platform.Hashes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ICE servers for WebRTC. TURN uses the time-limited shared-secret scheme that coturn implements
 * ({@code use-auth-secret}): username {@code <expiry>:<opaque user ref>}, password
 * {@code base64(HMAC-SHA1(secret, username))}. The TURN server checks them itself, so nothing is stored and
 * the credentials stop working at expiry. The username never contains a user id.
 */
@Component
class TurnCredentials {

	static final Duration LIFETIME = Duration.ofHours(1);

	private final List<String> stunUrls;

	private final List<String> turnUrls;

	private final String secret;

	private final Clock clock;

	TurnCredentials(@Value("${oneday.calls.stun-urls:stun:stun.l.google.com:19302}") List<String> stunUrls,
			@Value("${oneday.calls.turn-urls:}") List<String> turnUrls,
			@Value("${oneday.calls.turn-secret:}") String secret, Clock clock) {
		this.stunUrls = stunUrls.stream().filter(u -> !u.isBlank()).toList();
		this.turnUrls = turnUrls.stream().filter(u -> !u.isBlank()).toList();
		this.secret = secret;
		this.clock = clock;
	}

	IceServers forUser(String userId) {
		List<IceServer> servers = new ArrayList<>();
		if (!stunUrls.isEmpty()) {
			servers.add(new IceServer(stunUrls, null, null));
		}
		long expiry = clock.instant().plus(LIFETIME).getEpochSecond();
		if (!turnUrls.isEmpty() && !secret.isBlank()) {
			String username = expiry + ":" + Hashes.sha256("turn|" + userId).substring(0, 16);
			servers.add(new IceServer(turnUrls, username, hmacSha1(username)));
		}
		return new IceServers(servers, expiry);
	}

	String hmacSha1(String username) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1"); // what coturn's REST-API auth expects
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
			return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** In the shape of WebRTC's {@code RTCIceServer}. */
	record IceServer(List<String> urls, String username, String credential) {
	}

	record IceServers(List<IceServer> iceServers, long expiresAt) {
	}
}
