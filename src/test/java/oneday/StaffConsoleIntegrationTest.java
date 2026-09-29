package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;

/** Trust & Safety console: nobody is stuck in manual review, and every report reaches a human. */
class StaffConsoleIntegrationTest extends ApiTestSupport {

	@Test
	void staffRoutesAreClosedToEveryoneElse() throws Exception {
		String member = verifiedUser("Member");
		getAs(member, "/staff/reports").andExpect(status().isForbidden());
		getAs(member, "/staff/verification-queue").andExpect(status().isForbidden());

		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		getAs(moderator, "/staff/reports").andExpect(status().isOk());
		getAs(moderator, "/staff/audit").andExpect(status().isForbidden());
		getAs(moderator, "/staff/members").andExpect(status().isForbidden());

		// Revoking the role takes effect on the next request, even with the old token.
		jdbc.update("delete from staff_members where user_id = ?", userIdOf(moderator));
		getAs(moderator, "/staff/reports").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("STAFF_ONLY"));
	}

	@Test
	void unverifiedAccountsNeverReceiveStaffScopes() throws Exception {
		String token = register("Unverified Staff");
		jdbc.update("insert into staff_members (user_id, role, granted_at) values (?, 'ADMIN', ?)", userIdOf(token),
				java.sql.Timestamp.from(Instant.now()));
		String refreshed = JsonPath.read(body(postAs(token, "/verification/liveness",
				"{\"sessionToken\":\"dev-low-confidence\"}")), "$.token.token");
		getAs(refreshed, "/staff/reports").andExpect(status().isForbidden());
	}

	@Test
	void moderatorsResolveManualReviewInEveryDirection() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String unclear = register("Unclear");
		String approved = register("Approved");
		String minor = register("Minor");
		verify(unclear, "dev-low-confidence");
		verify(approved, "dev-low-confidence");
		verify(minor, "dev-looks-minor");

		getAs(moderator, "/staff/verification-queue").andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[*].latestOutcome", hasItem("POSSIBLE_MINOR")))
			.andExpect(jsonPath("$[?(@.displayName == 'Minor')].estimatedAge").value(hasItem(15)));

		// RETRY: the person may take the check again and pass.
		decide(moderator, unclear, "RETRY").andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
		String retried = verify(unclear, "dev-pass");
		postAs(retried, "/moments", "{\"kind\":\"TEXT\",\"caption\":\"hi\",\"shareScope\":\"FRIENDS_ONLY\"}")
			.andExpect(status().isCreated());

		// APPROVE: contact unlocks on the next token.
		decide(moderator, approved, "APPROVE").andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
		String approvedToken = verify(approved, "dev-pass");
		getAs(approvedToken, "/profile/me").andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

		// REJECT: likely minor is rejected and suspended; they cannot retry or use the app.
		decide(moderator, minor, "REJECT").andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));
		getAs(minor, "/profile/me").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));

		getAs(moderator, "/staff/verification-queue").andExpect(jsonPath("$", hasSize(0)));
		decide(moderator, approved, "APPROVE").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_UNDER_REVIEW"));
	}

	@Test
	void reportsAreTriagedByPriorityWithResponseTargetsAndCanSuspend() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(ravi, BLR_LAT, BLR_LON);
		String momentId = postPublicMoment(ravi, "coffee");

		report(asha, momentId, "SPAM");
		report(asha, momentId, "HARASSMENT");
		report(asha, momentId, "UNDERAGE_SUSPECTED");

		String queue = body(getAs(moderator, "/staff/reports").andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].priority").value("P0"))
			.andExpect(jsonPath("$[1].priority").value("P1"))
			.andExpect(jsonPath("$[2].priority").value("P2"))
			.andExpect(jsonPath("$[0].reportedDisplayName").value("Ravi"))
			.andExpect(jsonPath("$[0].reportsAgainstUser").value(3))
			.andExpect(jsonPath("$[0].overdue").value(false)));

		// P0 has a 2 h target; P1 has 24 h.
		clock.advance(Duration.ofHours(3));
		getAs(moderator, "/staff/reports").andExpect(jsonPath("$[0].overdue").value(true))
			.andExpect(jsonPath("$[1].overdue").value(false));

		List<String> ids = JsonPath.read(queue, "$[*].id");
		postAs(moderator, "/staff/reports/" + ids.get(1) + "/claim", null)
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.assigneeId").value(userIdOf(moderator)));
		postAs(moderator, "/staff/reports/" + ids.get(2) + "/resolve", "{\"action\":\"DISMISS\"}")
			.andExpect(jsonPath("$.status").value("DISMISSED"));
		postAs(moderator, "/staff/reports/" + ids.get(1) + "/resolve",
				"{\"action\":\"SUSPEND_USER\",\"note\":\"repeated harassment\"}")
			.andExpect(jsonPath("$.status").value("ACTIONED"))
			.andExpect(jsonPath("$.resolution").value("SUSPENDED"));

		// Suspension is immediate: Ravi is locked out and gone from discovery.
		getAs(ravi, "/profile/me").andExpect(status().isForbidden());
		locate(asha, BLR_LAT, BLR_LON);
		getAs(asha, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(0)));

		postAs(moderator, "/staff/reports/" + ids.get(1) + "/resolve", "{\"action\":\"DISMISS\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REPORT_CLOSED"));
		getAs(moderator, "/staff/reports").andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void adminsManageStaffAndEveryDecisionIsAudited() throws Exception {
		String admin = promote(verifiedUser("Admin"), "ADMIN");
		String helper = verifiedUser("Helper");
		String helperId = userIdOf(helper);

		perform(put("/staff/members/" + helperId), admin, "{\"role\":\"MODERATOR\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("MODERATOR"));
		String helperAsModerator = verify(helper, "dev-pass");
		getAs(helperAsModerator, "/staff/reports").andExpect(status().isOk());

		perform(put("/staff/members/" + userIdOf(admin)), admin, "{\"role\":\"MODERATOR\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLE"));

		// Suspend via report, then reinstate as admin.
		String reporter = verifiedUser("Reporter");
		locate(helper, BLR_LAT, BLR_LON);
		String reportId = JsonPath.read(body(postAs(reporter, "/safety/reports",
				"{\"momentId\":\"" + postPublicMoment(helperAsModerator, "chess") + "\",\"category\":\"SCAM_OR_FRAUD\"}")),
				"$.reportId");
		postAs(admin, "/staff/reports/" + reportId + "/resolve", "{\"action\":\"SUSPEND_USER\"}")
			.andExpect(status().isOk());
		getAs(helperAsModerator, "/staff/reports").andExpect(status().isForbidden());
		postAs(admin, "/staff/accounts/" + helperId + "/reinstate", "{\"note\":\"appeal upheld\"}")
			.andExpect(status().isNoContent());
		getAs(helperAsModerator, "/staff/reports").andExpect(status().isOk());

		deleteAs(admin, "/staff/members/" + helperId).andExpect(status().isNoContent());
		getAs(helperAsModerator, "/staff/reports").andExpect(status().isForbidden());

		String audit = body(getAs(admin, "/staff/audit").andExpect(status().isOk()));
		List<String> actions = JsonPath.read(audit, "$[*].action");
		assertThat(actions).contains("STAFF_GRANTED_MODERATOR", "REPORT_SUSPENDED", "ACCOUNT_SUSPENDED",
				"ACCOUNT_REINSTATED", "STAFF_REVOKED");
		List<String> reinstateNotes = JsonPath.read(audit, "$[?(@.action == 'ACCOUNT_REINSTATED')].note");
		assertThat(reinstateNotes).containsExactly("appeal upheld");
	}

	private org.springframework.test.web.servlet.ResultActions decide(String moderator, String subjectToken,
			String decision) throws Exception {
		return postAs(moderator, "/staff/verification/" + userIdOf(subjectToken) + "/decision",
				"{\"decision\":\"" + decision + "\"}");
	}

	private void report(String reporter, String momentId, String category) throws Exception {
		postAs(reporter, "/safety/reports", "{\"momentId\":\"" + momentId + "\",\"category\":\"" + category + "\"}")
			.andExpect(status().isCreated());
	}
}
