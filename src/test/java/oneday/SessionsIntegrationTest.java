package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Short-lived access tokens, rotating refresh tokens, and signing devices out. */
class SessionsIntegrationTest extends ApiTestSupport {

	@Test
	void refreshRotatesTheTokenAndPicksUpNewScopes() throws Exception {
		String email = emailOf(register("Asha"));
		Pair phone = pair(signInOn(email, "OneDay/1.0 (Android 16; Pixel 9)")
			.andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
			.andExpect(jsonPath("$.refreshToken").isNotEmpty()));

		// Verification upgrades the account inside the same session; the device keeps its refresh token.
		String verifyResponse = body(postAs(phone.access(), "/verification/liveness", "{\"sessionToken\":\"dev-pass\"}")
			.andExpect(jsonPath("$.token.refreshToken").doesNotExist()));
		String verified = JsonPath.read(verifyResponse, "$.token.token");
		assertThat(claim(verified, "sid")).isEqualTo(claim(phone.access(), "sid"));

		Pair renewed = pair(refresh(phone.refresh()).andExpect(status().isOk()));
		assertThat(renewed.refresh()).isNotEqualTo(phone.refresh());
		assertThat(claim(renewed.access(), "scope")).contains("verified");
		getAs(renewed.access(), "/profile/me").andExpect(status().isOk());

		// Only a hash of the secret is stored.
		String secret = renewed.refresh().substring(renewed.refresh().indexOf('.') + 1);
		List<String> stored = jdbc.queryForList("select refresh_hash from sessions", String.class);
		assertThat(stored).isNotEmpty().noneMatch(h -> h.contains(secret));
	}

	@Test
	void aReplayedRefreshTokenEndsTheSessionForWhoeverHoldsIt() throws Exception {
		String email = emailOf(register("Asha"));
		Pair original = pair(signInOn(email, "OneDay/1.0 (iOS 26)"));
		Pair legit = pair(refresh(original.refresh()).andExpect(status().isOk()));

		clock.advance(Duration.ofMinutes(5));
		refresh(original.refresh()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		refresh(legit.refresh()).andExpect(status().isUnauthorized());
		getAs(legit.access(), "/profile/me").andExpect(status().isUnauthorized());

		String fresh = pair(signInOn(email, "OneDay/1.0 (iOS 26)")).access();
		getAs(fresh, "/notices").andExpect(jsonPath("$[0].kind").value("SECURITY"));
	}

	@Test
	void aRetryAfterALostResponseIsNotTakenForTheft() throws Exception {
		String email = emailOf(register("Asha"));
		Pair original = pair(signInOn(email, "OneDay/1.0"));
		Pair lost = pair(refresh(original.refresh()).andExpect(status().isOk()));

		// The response never arrived, so the app retries with the token it still has.
		clock.advance(Duration.ofSeconds(5));
		Pair retried = pair(refresh(original.refresh()).andExpect(status().isOk()));
		Pair next = pair(refresh(retried.refresh()).andExpect(status().isOk()));
		getAs(next.access(), "/profile/me").andExpect(status().isOk());

		// The pair that was never delivered can't be used: whoever holds it has a copy it shouldn't.
		refresh(lost.refresh()).andExpect(status().isUnauthorized());
		getAs(next.access(), "/profile/me").andExpect(status().isUnauthorized());
	}

	@Test
	void signingOutEndsOneDeviceOrAllOfThem() throws Exception {
		String first = register("Asha");
		String email = emailOf(first);
		Pair phone = pair(signInOn(email, "OneDay/1.0 (Android 16)"));
		Pair laptop = pair(signInOn(email, "OneDay/1.0 (Web)"));
		Pair tablet = pair(signInOn(email, "OneDay/1.0 (iPadOS 26)"));

		getAs(phone.access(), "/auth/sessions").andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath("$[?(@.current == true)].device").value(hasItem("OneDay/1.0 (Android 16)")))
			.andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)));

		postAs(phone.access(), "/auth/logout", null).andExpect(status().isNoContent());
		getAs(phone.access(), "/profile/me").andExpect(status().isUnauthorized());
		refresh(phone.refresh()).andExpect(status().isUnauthorized());
		getAs(laptop.access(), "/profile/me").andExpect(status().isOk());

		// A lost tablet, signed out from the laptop. Someone else's session can't be touched.
		deleteAs(laptop.access(), "/auth/sessions/" + claim(tablet.access(), "sid")).andExpect(status().isNoContent());
		getAs(tablet.access(), "/profile/me").andExpect(status().isUnauthorized());
		String stranger = register("Ravi");
		deleteAs(stranger, "/auth/sessions/" + claim(laptop.access(), "sid")).andExpect(status().isNotFound());

		postAs(laptop.access(), "/auth/logout-all", null).andExpect(status().isNoContent());
		getAs(laptop.access(), "/profile/me").andExpect(status().isUnauthorized());
		getAs(first, "/profile/me").andExpect(status().isUnauthorized());
		getAs(stranger, "/profile/me").andExpect(status().isOk());
	}

	@Test
	void sessionsEndWhenIdleAndAtTheirMaximumAge() throws Exception {
		String email = emailOf(register("Asha"));
		Pair idle = pair(signInOn(email, "OneDay/1.0"));
		clock.advance(Duration.ofDays(61));
		refresh(idle.refresh()).andExpect(status().isUnauthorized());

		// Refreshing regularly keeps a session going, but never past 180 days.
		Pair active = pair(signInOn(email, "OneDay/1.0"));
		for (int i = 0; i < 3; i++) {
			clock.advance(Duration.ofDays(50));
			active = pair(refresh(active.refresh()).andExpect(status().isOk()));
		}
		clock.advance(Duration.ofDays(50));
		refresh(active.refresh()).andExpect(status().isUnauthorized());
	}

	@Test
	void theLeastRecentlyUsedDeviceIsSignedOutBeyondTheLimit() throws Exception {
		String first = register("Asha");
		String email = emailOf(first);
		String latest = null;
		for (int i = 0; i < 10; i++) {
			clock.advance(Duration.ofMinutes(10));
			latest = pair(signInOn(email, "device-" + i)).access();
		}
		getAs(latest, "/auth/sessions").andExpect(jsonPath("$", hasSize(10)));
		getAs(first, "/profile/me").andExpect(status().isUnauthorized());
	}

	@Test
	void passwordGuessingIsLimitedPerAccount() throws Exception {
		String email = emailOf(register("Asha"));
		for (int i = 0; i < 10; i++) {
			mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"guess-" + i + "\"}"))
				.andExpect(status().isUnauthorized());
		}
		login(email).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
		clock.advance(Duration.ofMinutes(61));
		login(email).andExpect(status().isOk());
	}

	@Test
	void theExportListsSignInsWithoutAnySecret() throws Exception {
		String email = emailOf(register("Asha"));
		Pair phone = pair(signInOn(email, "OneDay/1.0 (Android 16)"));
		postAs(phone.access(), "/auth/logout", null).andExpect(status().isNoContent());
		String token = pair(signInOn(email, "OneDay/1.0 (Web)")).access();

		String export = body(getAs(token, "/privacy/export").andExpect(jsonPath("$.signIns", hasSize(3)))
			.andExpect(jsonPath("$.signIns[?(@.endReason == 'SIGNED_OUT')].device")
				.value(hasItem("OneDay/1.0 (Android 16)"))));
		assertThat(export).doesNotContain(phone.refresh().substring(phone.refresh().indexOf('.') + 1))
			.doesNotContain("refreshHash");
	}

	// ---- helpers ------------------------------------------------------------------------------------

	private record Pair(String access, String refresh) {
	}

	private ResultActions signInOn(String email, String device) throws Exception {
		return mvc.perform(post("/auth/login").header("User-Agent", device)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk());
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private Pair pair(ResultActions result) throws Exception {
		String json = body(result);
		return new Pair(JsonPath.read(json, "$.token"), JsonPath.read(json, "$.refreshToken"));
	}

	private static String claim(String jwt, String name) {
		String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
		return JsonPath.read(payload, "$." + name);
	}
}
