package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Platform behaviour that keeps the API correct at scale: idempotent POST retries, and the per-request session
 * check served from a short cache that every sign-out still evicts at once.
 */
@TestPropertySource(properties = "oneday.security.session-cache-ttl=PT1M")
class PlatformScalabilityIntegrationTest extends ApiTestSupport {

	@Test
	void retriedPostsWithTheSameIdempotencyKeyHappenOnce() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);
		String moment = postPublicMoment(asha, "trek");
		String signal = "{\"momentId\":\"" + moment + "\",\"reaction\":\"SAME_HERE\"}";

		String first = body(keyed(ravi, "/signals", "retry-1", signal).andExpect(status().isCreated()));
		// The response was lost on a flaky network; the app retries with the same key.
		String second = body(keyed(ravi, "/signals", "retry-1", signal).andExpect(status().isCreated())
			.andExpect(header().string("Idempotent-Replayed", "true")));
		assertThat(second).isEqualTo(first);
		assertThat(jdbc.queryForObject("select count(*) from signals", Integer.class)).isEqualTo(1);

		// A different request under the same key is refused, not silently replayed.
		keyed(ravi, "/signals", "retry-1", signal.replace("SAME_HERE", "MADE_ME_SMILE"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
		// Without a key, the normal rules apply (one signal per moment).
		postAs(ravi, "/signals", signal).andExpect(status().isConflict());
		// Keys are per account.
		keyed(asha, "/moments", "retry-1", "{\"kind\":\"TEXT\",\"caption\":\"hi\",\"shareScope\":\"FRIENDS_ONLY\"}")
			.andExpect(status().isCreated());
		keyed(asha, "/moments", "x".repeat(65), "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
	}

	@Test
	void theSessionCacheNeverOutlivesASignOut() throws Exception {
		String asha = register("Asha");
		String phone = signIn(asha);
		getAs(phone, "/profile/me").andExpect(status().isOk()); // now cached as live
		postAs(phone, "/auth/logout", null).andExpect(status().isNoContent());
		getAs(phone, "/profile/me").andExpect(status().isUnauthorized()); // evicted on sign-out

		// Proof the cache is in use: a session ended behind the service's back stays valid until its TTL.
		String laptop = signIn(asha);
		getAs(laptop, "/profile/me").andExpect(status().isOk());
		jdbc.update("update sessions set ended_at = ? where user_id = ? and ended_at is null",
				Timestamp.from(clock.instant()), userIdOf(asha));
		getAs(laptop, "/profile/me").andExpect(status().isOk());
		clock.advance(Duration.ofMinutes(2));
		getAs(laptop, "/profile/me").andExpect(status().isUnauthorized());
	}

	private ResultActions keyed(String token, String url, String key, String json) throws Exception {
		return perform(post(url).header("Idempotency-Key", key), token, json);
	}
}
