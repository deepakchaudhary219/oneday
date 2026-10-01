package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import oneday.privacy.PrivacyService;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Erasure under a safety hold: the account disappears at once, exactly as if deleted, but the evidence
 * survives until the hold lifts. The person under review can't tell the difference.
 */
class LegalHoldIntegrationTest extends ApiTestSupport {

	@Autowired
	private PrivacyService privacy;

	@Test
	void anOpenP0ReportDefersErasureWithoutTippingOffTheAccountHolder() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String raviId = userIdOf(ravi);
		String raviEmail = JsonPath.read(body(getAs(ravi, "/privacy/export")), "$.account.email");
		locate(ravi, BLR_LAT, BLR_LON);
		String momentId = postPublicMoment(ravi, "coffee");
		String reportId = JsonPath.read(body(postAs(asha, "/safety/reports",
				"{\"momentId\":\"" + momentId + "\",\"category\":\"UNDERAGE_SUSPECTED\"}")), "$.reportId");

		// A control account with no hold, erased normally, for comparing responses.
		String control = verifiedUser("Control");
		deleteAs(control, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());

		deleteAs(ravi, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());

		// Indistinguishable from a real erasure, for the holder...
		MvcResult held = getAs(ravi, "/profile/me").andReturn();
		MvcResult erased = getAs(control, "/profile/me").andReturn();
		assertThat(held.getResponse().getStatus()).isEqualTo(erased.getResponse().getStatus()).isEqualTo(401);
		assertThat(held.getResponse().getContentAsString()).isEqualTo(erased.getResponse().getContentAsString());
		assertThat(held.getResponse().getHeader("WWW-Authenticate"))
			.isEqualTo(erased.getResponse().getHeader("WWW-Authenticate"));
		login(raviEmail).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		// ...their email is released (they could even sign up again)...
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + raviEmail + "\",\"password\":\"correct-horse-battery\","
					+ "\"dateOfBirth\":\"1998-04-12\",\"displayName\":\"Ravi\",\"consentVersion\":\"2026-09\"}"))
			.andExpect(status().isCreated());
		// ...and for everyone else: gone from discovery.
		locate(asha, BLR_LAT, BLR_LON);
		getAs(asha, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(0)));

		// But the evidence is intact, and Trust & Safety can see why.
		assertThat(count("select count(*) from moments where owner_id = ?", raviId)).isEqualTo(1);
		assertThat(count("select count(*) from users where id = ? and account_status = 'DEACTIVATED' "
				+ "and email is null", raviId)).isEqualTo(1);
		getAs(moderator, "/staff/erasures").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].userId").value(raviId))
			.andExpect(jsonPath("$[0].holdReason").value("Open P0 safety report"));

		privacy.completePendingErasures();
		assertThat(count("select count(*) from users where id = ?", raviId)).isEqualTo(1);

		// Once the review closes, the erasure completes by itself. The report itself is retained.
		postAs(moderator, "/staff/reports/" + reportId + "/resolve", "{\"action\":\"DISMISS\"}")
			.andExpect(status().isOk());
		privacy.completePendingErasures();
		assertThat(count("select count(*) from users where id = ?", raviId)).isZero();
		assertThat(count("select count(*) from moments where owner_id = ?", raviId)).isZero();
		assertThat(count("select count(*) from reports where reported_id = ?", raviId)).isEqualTo(1);
		getAs(moderator, "/staff/erasures").andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void enforcementEvidenceIsKeptForTheRetentionPeriod() throws Exception {
		String admin = promote(verifiedUser("Admin"), "ADMIN");
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String raviId = userIdOf(ravi);
		locate(ravi, BLR_LAT, BLR_LON);
		String reportId = JsonPath.read(body(postAs(asha, "/safety/reports",
				"{\"momentId\":\"" + postPublicMoment(ravi, "coffee") + "\",\"category\":\"HARASSMENT\"}")),
				"$.reportId");
		postAs(admin, "/staff/reports/" + reportId + "/resolve", "{\"action\":\"SUSPEND_USER\"}")
			.andExpect(status().isOk());

		// Suspension signs every device out; the holder can sign in again, export and ask for erasure.
		getAs(ravi, "/privacy/export").andExpect(status().isUnauthorized());
		ravi = signIn(ravi);
		getAs(ravi, "/privacy/export").andExpect(status().isOk());
		deleteAs(ravi, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());
		getAs(ravi, "/profile/me").andExpect(status().isUnauthorized());

		getAs(admin, "/staff/erasures")
			.andExpect(jsonPath("$[0].holdReason", startsWith("Enforcement evidence retained until")));
		postAs(admin, "/staff/accounts/" + raviId + "/reinstate", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ERASURE_PENDING"));

		clock.advance(Duration.ofDays(179));
		privacy.completePendingErasures();
		assertThat(count("select count(*) from users where id = ?", raviId)).isEqualTo(1);

		clock.advance(Duration.ofDays(2));
		privacy.completePendingErasures();
		assertThat(count("select count(*) from users where id = ?", raviId)).isZero();
	}

	@Test
	void accountsWithoutAHoldAreErasedImmediately() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String asha = verifiedUser("Asha");
		String raviId = userIdOf(asha);
		postAs(verifiedUser("Ravi"), "/safety/reports", "{\"momentId\":\"" + postPublicMomentAfterLocating(asha)
				+ "\",\"category\":\"SPAM\"}").andExpect(status().isCreated());
		deleteAs(asha, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());
		assertThat(count("select count(*) from users where id = ?", raviId)).isZero();
		getAs(moderator, "/staff/erasures").andExpect(jsonPath("$", hasSize(0)));
	}

	private String postPublicMomentAfterLocating(String token) throws Exception {
		locate(token, BLR_LAT, BLR_LON);
		return postPublicMoment(token, "coffee");
	}

	private long count(String sql, Object... args) {
		Long n = jdbc.queryForObject(sql, Long.class, args);
		return n == null ? 0 : n;
	}
}
