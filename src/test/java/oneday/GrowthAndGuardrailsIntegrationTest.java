package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.privacy.PrivacyService;
import oneday.support.ApiTestSupport;
import oneday.verification.Reverification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The remaining backlog: re-verification every 90 days, the wellbeing guardrail and Weekly Meaningful
 * Actives, Festival Seasons, the Weekly Recap, story retention, and Right Now.
 */
class GrowthAndGuardrailsIntegrationTest extends ApiTestSupport {

	@Autowired
	private Reverification reverification;

	@Autowired
	private PrivacyService privacy;

	@Test
	void verificationLapsesAfterNinetyDaysWithOneReminder() throws Exception {
		String asha = verifiedUser("Asha");
		locate(asha, BLR_LAT, BLR_LON);
		clock.advance(Duration.ofDays(84));
		asha = signIn(asha);
		assertThat(reverification.remind()).isEqualTo(1);
		assertThat(reverification.remind()).isZero(); // one reminder, never a stream of nags
		getAs(asha, "/notices").andExpect(jsonPath("$[0].kind").value("ACCOUNT"))
			.andExpect(jsonPath("$[0].message", containsString("re-check is due within 7 days")));
		assertThat(reverification.expire()).isZero();
		postText(asha).andExpect(status().isCreated());

		clock.advance(Duration.ofDays(7));
		assertThat(reverification.expire()).isEqualTo(1);
		postText(asha).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REVERIFICATION_REQUIRED"));
		getAs(asha, "/profile/me").andExpect(jsonPath("$.verificationStatus").value("EXPIRED"));
		getAs(asha, "/pulse").andExpect(status().isOk()); // browsing keeps working

		String fresh = verify(asha, "dev-pass");
		postText(fresh).andExpect(status().isCreated());
		getAs(fresh, "/profile/me").andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
	}

	@Test
	void theWellbeingQuestionIsRareAndWeeklyMeaningfulActivesCountTwoWayMoments() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		verifiedUser("Lurker");
		getAs(asha, "/wellbeing/check").andExpect(jsonPath("$.ask").value(true))
			.andExpect(jsonPath("$.question", containsString("well spent")));
		postAs(asha, "/wellbeing/answer", "{\"wellSpent\":false}").andExpect(status().isOk())
			.andExpect(jsonPath("$.message", containsString("You're in control")));
		postAs(asha, "/wellbeing/answer", "{\"wellSpent\":true}").andExpect(status().isConflict());
		getAs(asha, "/wellbeing/check").andExpect(jsonPath("$.ask").value(false))
			.andExpect(jsonPath("$.question").value(nullValue()));
		postAs(ravi, "/wellbeing/answer", "{\"wellSpent\":true}").andExpect(status().isOk());

		// A reveal makes both people meaningfully active this week; a one-way signal alone would not.
		connect(asha, ravi);
		deliverEvents();
		String admin = promote(verifiedUser("Admin"), "ADMIN");
		getAs(admin, "/staff/metrics/engagement?weeks=2").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].meaningfulActives").value(2))
			.andExpect(jsonPath("$[0].wellbeingAnswers").value(2))
			.andExpect(jsonPath("$[0].wellSpentShare").value(0.5));
		getAs(asha, "/staff/metrics/engagement").andExpect(status().isForbidden());
		getAs(asha, "/privacy/export").andExpect(jsonPath("$.wellbeingAnswers", hasSize(1)));
	}

	@Test
	void festivalSeasonsDrivePromptsAndLabelTheMap() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
		postAs(moderator, "/staff/festivals", festival("Onam", "in-kl", today.minusDays(2), today.plusDays(7)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.homeRegion").value("IN-KL"));
		postAs(moderator, "/staff/festivals", festival("Too long", null, today, today.plusDays(40)))
			.andExpect(status().isUnprocessableContent());

		String viewer = keralite("Viewer", 12.9352, 77.6245);
		String asha = keralite("Asha", 12.9716, 77.5946);
		String ravi = keralite("Ravi", 12.9760, 77.5946);
		String prompt = body(getAs(asha, "/prompts/today").andExpect(jsonPath("$.festival").value("Onam"))
			.andExpect(jsonPath("$.rootsPrompt").value(true))
			.andExpect(jsonPath("$.text").value("Show us your Onam sadhya")));
		String key = JsonPath.read(prompt, "$.promptKey");
		assertThat(key).startsWith("f:").hasSizeLessThanOrEqualTo(48);
		getAs(moderator, "/prompts/today").andExpect(jsonPath("$.festival").value(nullValue()));

		for (String t : new String[] { asha, ravi }) {
			postAs(t, "/moments", """
					{"kind":"TEXT","caption":"Sadhya!","activityTag":"food","shareScope":"PUBLIC_DISCOVERY","promptKey":"%s"}
					""".formatted(key)).andExpect(status().isCreated());
		}
		updateProfile(viewer, "{\"discoveryRadiusKm\":15}");
		getAs(viewer, "/map/stories").andExpect(jsonPath("$.clusters[0].season").value("Onam"))
			.andExpect(jsonPath("$.clusters[0].fromYourHomeRegion").value(2));
		getAs(moderator, "/staff/festivals").andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void theWeeklyRecapTellsLastWeekAsAShortStory() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String quiet = verifiedUser("Quiet");
		connect(asha, ravi);
		deliverEvents();
		clock.advance(Duration.ofDays(7));
		getAs(asha, "/ledger/week").andExpect(status().isOk())
			.andExpect(jsonPath("$.ready").value(true))
			.andExpect(jsonPath("$.highlight").value("You made a new connection."))
			.andExpect(jsonPath("$.lines", hasItem("1 new connection")))
			.andExpect(jsonPath("$.lines", hasItem("1 moment shared")))
			.andExpect(jsonPath("$.ending", containsString("real week")));
		getAs(quiet, "/ledger/week").andExpect(jsonPath("$.ready").value(false))
			.andExpect(jsonPath("$.ending", containsString("quiet week, and that's okay")));
	}

	@Test
	void expiredStoriesAreDeletedUnlessTheAccountIsUnderASafetyHold() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);
		postText(asha).andExpect(status().isCreated());
		String raviMoment = JsonPath.read(body(postText(ravi)), "$.id");
		postAs(asha, "/safety/reports", "{\"momentId\":\"" + raviMoment + "\",\"category\":\"THREAT_OR_VIOLENCE\"}")
			.andExpect(status().isCreated());

		clock.advance(Duration.ofDays(10));
		privacy.purgeExpiredMoments();
		assertThat(momentsOf(asha)).isEqualTo(1); // expired, but within the retention period
		clock.advance(Duration.ofDays(6));
		privacy.purgeExpiredMoments();
		assertThat(momentsOf(asha)).isZero(); // no location history builds up
		assertThat(momentsOf(ravi)).isEqualTo(1); // held as evidence while the P0 report is open
	}

	@Test
	void rightNowConnectsPeopleUpForTheSameThingWithConsent() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String meera = verifiedUser("Meera");
		for (String t : new String[] { asha, ravi, meera }) {
			locate(t, BLR_LAT, BLR_LON);
		}
		postAs(asha, "/right-now", "{\"activity\":\"Badminton\",\"minutes\":200}").andExpect(status().isUnprocessableContent());
		postAs(asha, "/right-now", "{\"activity\":\"Badminton\",\"minutes\":60}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.activity").value("badminton"))
			.andExpect(jsonPath("$.timeLeft").value("for about an hour"));

		String nearby = body(getAs(ravi, "/right-now").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].firstName").value("Asha"))
			.andExpect(jsonPath("$[0].band").value("UNDER_1_KM"))
			.andExpect(jsonPath("$[0].youAsked").value(false)));
		assertThat(nearby).doesNotContain(userIdOf(asha), "12.93");
		getAs(asha, "/right-now").andExpect(jsonPath("$", hasSize(0))); // not your own
		String sessionId = JsonPath.read(nearby, "$[0].id");

		postAs(ravi, "/right-now/" + sessionId + "/join", null).andExpect(status().isOk());
		postAs(ravi, "/right-now/" + sessionId + "/join", null).andExpect(status().isConflict());
		postAs(meera, "/right-now/" + sessionId + "/join", null).andExpect(status().isOk());
		String requests = body(getAs(asha, "/right-now/requests").andExpect(jsonPath("$", hasSize(2))));
		getAs(asha, "/right-now/mine").andExpect(jsonPath("$.requests").value("2"));

		// Accepting connects; declining is silent.
		List<String> raviIds = JsonPath.read(requests, "$[?(@.firstName=='Ravi')].requestId");
		List<String> meeraIds = JsonPath.read(requests, "$[?(@.firstName=='Meera')].requestId");
		String raviRequest = raviIds.get(0);
		String meeraRequest = meeraIds.get(0);
		postAs(asha, "/right-now/requests/" + raviRequest + "/accept", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.seedContext").value("Connected over: badminton, right now"));
		postAs(asha, "/right-now/requests/" + meeraRequest + "/decline", null).andExpect(status().isNoContent());
		getAs(ravi, "/connections").andExpect(jsonPath("$", hasSize(1)));
		getAs(meera, "/connections").andExpect(jsonPath("$", hasSize(0)));
		String meeraView = body(getAs(meera, "/right-now").andExpect(jsonPath("$[0].youAsked").value(true)));
		assertThat(meeraView).doesNotContain("DECLINED", "declined");
		// Now connected, Ravi no longer sees Asha in Right Now (they can just chat).
		getAs(ravi, "/right-now").andExpect(jsonPath("$", hasSize(0)));

		clock.advance(Duration.ofMinutes(61));
		getAs(meera, "/right-now").andExpect(jsonPath("$", hasSize(0)));
		getAs(asha, "/right-now/mine").andExpect(status().isNoContent());
	}

	private String keralite(String name, double lat, double lon) throws Exception {
		String token = verifiedUser(name);
		updateProfile(token, "{\"homeRegion\":\"IN-KL\"}");
		locate(token, lat, lon);
		return token;
	}

	private static String festival(String name, String region, LocalDate from, LocalDate to) {
		String regionJson = region == null ? "null" : "\"" + region + "\"";
		return """
				{"name":"%s","homeRegion":%s,"startsOn":"%s","endsOn":"%s","promptText":"Show us your Onam sadhya","activityHint":"food"}
				""".formatted(name, regionJson, from, to);
	}

	private ResultActions postText(String token) throws Exception {
		return postAs(token, "/moments",
				"{\"kind\":\"TEXT\",\"caption\":\"Out and about\",\"activityTag\":\"walk\",\"shareScope\":\"PUBLIC_DISCOVERY\"}");
	}

	private int momentsOf(String token) {
		return jdbc.queryForObject("select count(*) from moments where owner_id = ?", Integer.class, userIdOf(token));
	}
}
