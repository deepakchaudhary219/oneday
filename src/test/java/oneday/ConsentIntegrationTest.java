package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * DPDP s.6 consent: recorded per purpose at the affirmative action, withdrawable as easily as it was given,
 * withdrawal deletes the purpose's data at once and blocks further processing until re-granted, and the
 * proof outlives erasure without naming the person.
 */
@SpringBootTest
class ConsentIntegrationTest extends ApiTestSupport {

	private static final String PURPOSE = "$[?(@.purpose=='%s')].%s";

	@Test
	void sharingLocationRecordsConsentAndWithdrawingItDeletesTheCellUntilGrantedAgain() throws Exception {
		String asha = verifiedUser("Asha");
		getAs(asha, "/consents").andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath(PURPOSE.formatted("LOCATION_DISCOVERY", "state")).value("NOT_GIVEN"))
			.andExpect(jsonPath(PURPOSE.formatted("LOCATION_DISCOVERY", "processes"), hasSize(1)))
			.andExpect(jsonPath(PURPOSE.formatted("LOCATION_DISCOVERY", "onWithdrawal"), hasSize(1)));

		locate(asha, BLR_LAT, BLR_LON);
		getAs(asha, "/consents").andExpect(jsonPath(PURPOSE.formatted("LOCATION_DISCOVERY", "state")).value("GRANTED"));

		deleteAs(asha, "/consents/LOCATION_DISCOVERY").andExpect(status().isOk())
			.andExpect(jsonPath(PURPOSE.formatted("LOCATION_DISCOVERY", "state")).value("WITHDRAWN"));
		getAs(asha, "/location").andExpect(status().isNotFound());
		perform(put("/location"), asha, "{\"lat\":12.9352,\"lon\":77.6245}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONSENT_WITHDRAWN"));

		postAs(asha, "/consents/LOCATION_DISCOVERY", null).andExpect(status().isOk())
			.andExpect(jsonPath(PURPOSE.formatted("LOCATION_DISCOVERY", "state")).value("GRANTED"));
		locate(asha, BLR_LAT, BLR_LON);

		getAs(asha, "/consents/history").andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].action").value("GRANTED"))
			.andExpect(jsonPath("$[0].source").value("APP_ACTION"))
			.andExpect(jsonPath("$[0].noticeVersion").value("2026-09"))
			.andExpect(jsonPath("$[1].action").value("WITHDRAWN"))
			.andExpect(jsonPath("$[1].source").value("SETTINGS"))
			.andExpect(jsonPath("$[2].action").value("GRANTED"))
			.andExpect(jsonPath("$[2].source").value("SETTINGS"));
		getAs(asha, "/privacy/export").andExpect(jsonPath("$.consentHistory", hasSize(3)));
	}

	@Test
	void withdrawingDatingPreferencesClearsThemAndEndsSparksSilently() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connection = connect(asha, ravi);
		updateProfile(asha, "{\"gender\":\"WOMAN\",\"interestedIn\":[\"MAN\"]}");
		mutualSpark(asha, ravi, connection);
		getAs(ravi, "/connections").andExpect(jsonPath("$[0].mutualSpark").value(true));

		deleteAs(asha, "/consents/DATING_PREFERENCES").andExpect(status().isOk());
		getAs(ravi, "/connections").andExpect(jsonPath("$[0].mutualSpark").value(false));
		getAs(asha, "/profile/me").andExpect(jsonPath("$.datingLens").value(false))
			.andExpect(jsonPath("$.gender").doesNotExist())
			.andExpect(jsonPath("$.interestedIn", hasSize(0)));
		perform(patch("/profile/me"), asha, "{\"datingLens\":true}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("Privacy settings")));
		// Other profile edits are unaffected.
		updateProfile(asha, "{\"bio\":\"Weekend trekker\"}");

		postAs(asha, "/consents/DATING_PREFERENCES", null).andExpect(status().isOk());
		updateProfile(asha, "{\"datingLens\":true}");
		postAs(asha, "/connections/" + connection + "/spark", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.mutualSpark").value(true)); // only her data went; Ravi's spark is his
	}

	@Test
	void rootsAndWellbeingWithdrawalsDeleteTheirDataAndStopTheProcessing() throws Exception {
		String asha = verifiedUser("Asha");
		updateProfile(asha, "{\"homeRegion\":\"IN-KL\",\"languages\":[\"ml\",\"en\"]}");
		postAs(asha, "/wellbeing/answer", "{\"wellSpent\":true}").andExpect(status().isOk());
		getAs(asha, "/consents").andExpect(jsonPath(PURPOSE.formatted("ROOTS_AND_LANGUAGES", "state")).value("GRANTED"))
			.andExpect(jsonPath(PURPOSE.formatted("WELLBEING_SURVEY", "state")).value("GRANTED"));

		deleteAs(asha, "/consents/ROOTS_AND_LANGUAGES").andExpect(status().isOk());
		getAs(asha, "/profile/me").andExpect(jsonPath("$.homeRegion").doesNotExist())
			.andExpect(jsonPath("$.languages", hasSize(0)));

		deleteAs(asha, "/consents/WELLBEING_SURVEY").andExpect(status().isOk());
		getAs(asha, "/privacy/export").andExpect(jsonPath("$.wellbeingAnswers", hasSize(0)));
		clock.advance(Duration.ofDays(30));
		getAs(asha, "/wellbeing/check").andExpect(jsonPath("$.ask").value(false));
		// Withdrawing twice is a no-op, not a second ledger row.
		deleteAs(asha, "/consents/WELLBEING_SURVEY").andExpect(status().isOk());
		getAs(asha, "/consents/history").andExpect(jsonPath("$", hasSize(4)));
		postAs(asha, "/consents/NOT_A_PURPOSE", null).andExpect(status().isBadRequest());
	}

	@Test
	void erasureKeepsTheProofOfConsentButNotWhoGaveIt() throws Exception {
		String asha = verifiedUser("Asha");
		locate(asha, BLR_LAT, BLR_LON);
		String userId = userIdOf(asha);
		deleteAs(asha, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from consent_records where user_id = ?", Integer.class, userId))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from consent_records where user_id like 'erased:%'",
				Integer.class)).isEqualTo(1);
	}
}
