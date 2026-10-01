package oneday.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import oneday.common.ApiException;
import oneday.integrations.FakeVendorServer;
import oneday.integrations.FakeVendorServer.Response;
import oneday.integrations.GoogleServiceAccount;

import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Play Integrity verdicts and request binding, and the guard's off / monitor / enforce rollout modes. */
class AttestationTest {

	private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

	@TempDir
	Path dir;

	@Test
	void playIntegrityTrustsOnlyOurAppOnARealDeviceWithAFreshTokenMintedForThisRequest() throws Exception {
		try (FakeVendorServer google = new FakeVendorServer()) {
			JsonMapper json = JsonMapper.builder().build();
			HttpClient http = HttpClient.newHttpClient();
			Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
			google.route("/v1/app.oneday:decodeIntegrityToken", call -> {
				String token = call.body().contains("emulator") ? "emulator" : call.body().contains("stale") ? "stale"
						: call.body().contains("sideloaded") ? "sideloaded"
								: call.body().contains("for-location") ? "for-location" : "good";
				long ts = token.equals("stale") ? NOW.minusSeconds(3600).toEpochMilli() : NOW.toEpochMilli();
				String app = token.equals("sideloaded") ? "UNRECOGNIZED_VERSION" : "PLAY_RECOGNIZED";
				String device = token.equals("emulator") ? "[]" : "[\"MEETS_DEVICE_INTEGRITY\"]";
				// The app put SHA256(challenge|action) in requestHash when it asked Google for the token.
				String challenge = call.body().replaceAll("(?s).*\\bchallenge=(\\w+).*", "$1");
				String hash = requestHash(challenge, token.equals("for-location") ? "location" : "signup");
				return new Response(200, """
						{"tokenPayloadExternal":{"requestDetails":{"requestPackageName":"app.oneday","timestampMillis":"%d",
						 "requestHash":"%s"},
						 "appIntegrity":{"appRecognitionVerdict":"%s"},"deviceIntegrity":{"deviceRecognitionVerdict":%s}}}
						""".formatted(ts, hash, app, device));
			});
			AttestationChallenges challenges = mock(AttestationChallenges.class);
			when(challenges.consume(startsWith("c"))).thenReturn(true);
			PlayIntegrityAttestor play = new PlayIntegrityAttestor(
					new GoogleServiceAccount(google.credentials(dir), http, json, clock), http, json, clock, "app.oneday",
					google.baseUrl(), challenges);
			assertThat(play.handles("play.c1.x")).isTrue();
			assertThat(play.handles("appattest.k.c.a")).isFalse();
			assertThat(play.verify(token("c1", "good"), "signup")).isEqualTo(DeviceAttestor.Verdict.TRUSTED);
			assertThat(play.verify(token("c2", "emulator"), "signup")).isEqualTo(DeviceAttestor.Verdict.FAILED);
			assertThat(play.verify(token("c3", "stale"), "signup")).isEqualTo(DeviceAttestor.Verdict.FAILED);
			assertThat(play.verify(token("c4", "sideloaded"), "signup")).isEqualTo(DeviceAttestor.Verdict.FAILED);
			assertThat(play.verify(token("c5", "for-location"), "signup")).isEqualTo(DeviceAttestor.Verdict.FAILED);
			assertThat(play.verify(token("x6", "good"), "signup")).isEqualTo(DeviceAttestor.Verdict.FAILED); // spent
			assertThat(play.verify("play.c7", "signup")).isEqualTo(DeviceAttestor.Verdict.FAILED);
		}
	}

	/** What the app sends: {@code play.<challenge>.<token>}; the fake token embeds the challenge for routing. */
	private static String token(String challenge, String kind) {
		return "play." + challenge + ".eyJ." + kind + ".challenge=" + challenge + ".x.y";
	}

	private static String requestHash(String challenge, String action) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(AppAttestVerifier.sha256((challenge + "|" + action).getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void monitorModeMeasuresButNeverBlocksWhileEnforceModeDoes() {
		SimpleMeterRegistry metrics = new SimpleMeterRegistry();
		StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("dev", new DevDeviceAttestor()));
		AttestationGuard monitor = new AttestationGuard(beans.getBeanProvider(DeviceAttestor.class), metrics, "monitor");
		monitor.check(null, "signup");
		monitor.check("tampered", "location");
		monitor.check("dev-ok", "location");
		assertThat(metrics.get("oneday.attestation").tag("outcome", "missing").counter().count()).isEqualTo(1);
		assertThat(metrics.get("oneday.attestation").tag("outcome", "failed").counter().count()).isEqualTo(1);
		assertThat(metrics.get("oneday.attestation").tag("outcome", "trusted").counter().count()).isEqualTo(1);

		AttestationGuard enforce = new AttestationGuard(beans.getBeanProvider(DeviceAttestor.class), metrics, "enforce");
		enforce.check("dev-ok", "signup");
		assertThatThrownBy(() -> enforce.check(null, "signup")).isInstanceOf(ApiException.class)
			.hasMessageContaining("official OneDay app");
		assertThatThrownBy(() -> enforce.check("tampered", "location")).isInstanceOf(ApiException.class);

		StaticListableBeanFactory none = new StaticListableBeanFactory();
		assertThatThrownBy(() -> new AttestationGuard(none.getBeanProvider(DeviceAttestor.class), metrics, "enforce"))
			.isInstanceOf(IllegalStateException.class);
	}
}
