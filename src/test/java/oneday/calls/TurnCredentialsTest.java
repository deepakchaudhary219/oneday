package oneday.calls;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class TurnCredentialsTest {

	@Test
	void turnCredentialsFollowTheSharedSecretSchemeAndNameNoUser() throws Exception {
		Instant now = Instant.parse("2026-10-01T10:00:00Z");
		TurnCredentials turn = new TurnCredentials(List.of("stun:stun.example:3478"),
				List.of("turn:turn.example:3478?transport=udp", "turns:turn.example:5349"), "s3cret",
				Clock.fixed(now, ZoneOffset.UTC));
		var servers = turn.forUser("0190a1b2-user-id");
		assertThat(servers.iceServers()).hasSize(2);
		var relay = servers.iceServers().get(1);
		assertThat(relay.username()).startsWith(now.plus(TurnCredentials.LIFETIME).getEpochSecond() + ":")
			.doesNotContain("user-id");
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec("s3cret".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
		assertThat(relay.credential()).isEqualTo(
				Base64.getEncoder().encodeToString(mac.doFinal(relay.username().getBytes(StandardCharsets.UTF_8))));
	}
}
