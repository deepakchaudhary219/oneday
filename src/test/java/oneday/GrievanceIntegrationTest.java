package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;

/** Grievance redressal: acknowledged on filing, answered by a deadline, with a route onwards. */
class GrievanceIntegrationTest extends ApiTestSupport {

	@Test
	void aSuspendedAccountCanAppealAndHearsBack() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(ravi, BLR_LAT, BLR_LON);
		String report = JsonPath.read(body(postAs(asha, "/safety/reports",
				"{\"momentId\":\"" + postPublicMoment(ravi, "coffee") + "\",\"category\":\"SCAM_OR_FRAUD\"}")),
				"$.reportId");
		postAs(moderator, "/staff/reports/" + report + "/resolve", "{\"action\":\"SUSPEND_USER\"}")
			.andExpect(status().isOk());

		// Suspended and signed out, Ravi signs in to the restricted account and appeals.
		String restricted = signIn(ravi);
		Instant filedAt = clock.instant();
		String filed = body(postAs(restricted, "/grievances",
				"{\"category\":\"ACCOUNT_ACTION\",\"description\":\"I was suspended by mistake, that was a real "
						+ "event I was organising.\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.reference", matchesPattern("G-[0-9A-HJKMNP-TV-Z]{8}")))
			.andExpect(jsonPath("$.status").value("OPEN")));
		assertThat(Instant.parse(JsonPath.read(filed, "$.resolveBy"))).isEqualTo(filedAt.plus(Duration.ofDays(15)));
		String reference = JsonPath.read(filed, "$.reference");
		// Filing is the acknowledgement, and the notice is its record.
		getAs(restricted, "/notices").andExpect(jsonPath("$[0].kind").value("GRIEVANCE_UPDATE"))
			.andExpect(jsonPath("$[0].message", containsString(reference)));

		String queue = body(getAs(moderator, "/staff/grievances").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].accountStatus").value("SUSPENDED"))
			.andExpect(jsonPath("$[0].overdue").value(false)));
		String id = JsonPath.read(queue, "$[0].id");
		clock.advance(Duration.ofHours(3));
		postAs(moderator, "/staff/grievances/" + id + "/answer",
				"{\"outcome\":\"UPHELD\",\"response\":\"You're right: we've reviewed it and lifted the suspension.\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESOLVED"));
		postAs(moderator, "/staff/grievances/" + id + "/answer", "{\"outcome\":\"NOT_UPHELD\",\"response\":\"x\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_RESOLVED"));
		getAs(moderator, "/staff/grievances").andExpect(jsonPath("$", hasSize(0)));

		getAs(restricted, "/grievances").andExpect(jsonPath("$[0].outcome").value("UPHELD"))
			.andExpect(jsonPath("$[0].response", containsString("lifted the suspension")))
			.andExpect(jsonPath("$[0].ifNotSatisfied", containsString("Grievance Appellate Committee")));
		getAs(restricted, "/notices").andExpect(jsonPath("$[0].message", containsString("answered")));

		String admin = promote(verifiedUser("Admin"), "ADMIN");
		List<String> actions = JsonPath.read(body(getAs(admin, "/staff/audit")), "$[*].action");
		assertThat(actions).contains("GRIEVANCE_UPHELD");
	}

	@Test
	void deadlinesFollowTheLawAndOverdueWorkIsFlagged() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String priya = register("Priya");
		file(priya, "OTHER", "The app crashes when I open the map.");
		file(priya, "PRIVACY", "Please correct the date of birth on my account.");
		file(priya, "CONTENT_REMOVAL", "This story shows my shop's name next to false claims.");
		file(priya, "INTIMATE_IMAGERY", "Someone posted a private photo of me.");

		getAs(moderator, "/staff/grievances").andExpect(jsonPath("$[*].category").value(org.hamcrest.Matchers
			.contains("INTIMATE_IMAGERY", "CONTENT_REMOVAL", "OTHER", "PRIVACY")));

		clock.advance(Duration.ofHours(25));
		getAs(moderator, "/staff/grievances").andExpect(jsonPath("$[0].overdue").value(true))
			.andExpect(jsonPath("$[1].overdue").value(false));

		List<String> privacy = JsonPath.read(body(getAs(priya, "/grievances")), "$[?(@.category == 'PRIVACY')].id");
		postAs(moderator, "/staff/grievances/" + privacy.get(0) + "/answer",
				"{\"outcome\":\"UPHELD\",\"response\":\"Corrected.\"}").andExpect(status().isOk());
		getAs(priya, "/grievances").andExpect(jsonPath("$[?(@.category == 'PRIVACY')].ifNotSatisfied")
			.value(org.hamcrest.Matchers.hasItem(containsString("Data Protection Board"))));
	}

	@Test
	void theOfficerIsPublishedAndFilingIsBoundedAndPrivate() throws Exception {
		mvc.perform(get("/grievances/officer")).andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Test Grievance Officer"))
			.andExpect(jsonPath("$.email").value("grievance@oneday.test"));

		String asha = register("Asha");
		postAs(asha, "/grievances", "{\"category\":\"OTHER\",\"description\":\"  \"}")
			.andExpect(status().isBadRequest());
		postAs(asha, "/grievances", "{\"category\":\"OTHER\",\"description\":\"" + "x".repeat(2001) + "\"}")
			.andExpect(status().isBadRequest());
		for (int i = 0; i < 5; i++) {
			file(asha, "OTHER", "Feedback number " + i);
		}
		postAs(asha, "/grievances", "{\"category\":\"OTHER\",\"description\":\"One more\"}")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("GRIEVANCE_RATE_LIMITED"));

		// Only staff see the queue; members see only their own grievances.
		getAs(asha, "/staff/grievances").andExpect(status().isForbidden());
		getAs(register("Ravi"), "/grievances").andExpect(jsonPath("$", hasSize(0)));

		getAs(asha, "/privacy/export").andExpect(jsonPath("$.grievances", hasSize(5)));
		deleteAs(asha, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from grievances", Long.class)).isZero();
	}

	private void file(String token, String category, String description) throws Exception {
		postAs(token, "/grievances", "{\"category\":\"" + category + "\",\"description\":\"" + description + "\"}")
			.andExpect(status().isCreated());
	}
}
