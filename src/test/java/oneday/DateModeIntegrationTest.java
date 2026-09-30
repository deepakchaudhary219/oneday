package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;

import oneday.dates.DateService;
import oneday.sms.DevSmsSender;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Date Mode (blueprint v2 §5.3): plan → confirm → time-boxed, mutually consented exact location → trusted
 * contact → check-ins and escalation → end → Mutual Debrief, plus SOS, blocks and Meeting Points.
 */
class DateModeIntegrationTest extends ApiTestSupport {

	private static final String CONTACT = "+919876543210";

	@Autowired
	private DateService dates;

	@Autowired
	private DevSmsSender sms;

	@Test
	void aConfirmedPlanFromTimeBoxedLocationToMutualDebrief() throws Exception {
		String asha = verifiedUser("Asha Nair");
		String ravi = verifiedUser("Ravi Menon");
		String connectionId = connect(asha, ravi);
		Instant start = clock.instant().plus(Duration.ofHours(2));

		String dateId = JsonPath.read(body(propose(ravi, connectionId, start, start.plus(Duration.ofHours(2)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PROPOSED"))
			.andExpect(jsonPath("$.youProposed").value(true))
			.andExpect(jsonPath("$.partnerFirstName").value("Asha"))
			.andExpect(jsonPath("$.emergencyNumber").value("112"))), "$.id");
		propose(asha, connectionId, start, start.plusSeconds(3600)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PLAN_ALREADY_OPEN"));

		// Only the invited person confirms.
		postAs(ravi, "/dates/" + dateId + "/accept", null).andExpect(status().isConflict());
		postAs(asha, "/dates/" + dateId + "/accept", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONFIRMED"));

		share(asha, dateId, true);
		share(ravi, dateId, true);
		// Before the time box opens there is no exact location at all.
		position(asha, dateId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUTSIDE_TIME_BOX"));

		// Asha's trusted contact gets a private link that follows her side of the plan.
		String contact = body(perform(put("/dates/" + dateId + "/trusted-contact"), asha,
				"{\"name\":\"Meera\",\"phone\":\"98765 43210\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.texted").value(true))
			.andExpect(jsonPath("$.phone").value("•••• 3210")));
		String shareUrl = JsonPath.read(contact, "$.shareUrl");
		String sharePath = shareUrl.substring(shareUrl.indexOf("/date-share/"));
		assertThat(sms.lastMessageTo(CONTACT)).hasValueSatisfying(m -> assertThat(m).contains("Asha", shareUrl, "112"));
		mvc.perform(get(sharePath)).andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Asha"))
			.andExpect(jsonPath("$.meetingWith").value("Ravi"))
			.andExpect(jsonPath("$.location").value(nullValue()));

		// Inside the time box, with both sharing, each sees the other's current point.
		clock.advance(Duration.ofMinutes(105));
		position(asha, dateId).andExpect(status().isOk());
		getAs(ravi, "/dates/" + dateId).andExpect(jsonPath("$.locationWindowOpen").value(true))
			.andExpect(jsonPath("$.partnerSharing").value(true))
			.andExpect(jsonPath("$.partnerLocation.lat").value(12.9352));
		mvc.perform(get(sharePath)).andExpect(jsonPath("$.location.lat").value(12.9352));
		// Consent is mutual: once Ravi stops sharing, he stops seeing Asha too.
		share(ravi, dateId, false);
		getAs(ravi, "/dates/" + dateId).andExpect(jsonPath("$.partnerLocation").value(nullValue()));
		getAs(asha, "/dates/" + dateId).andExpect(jsonPath("$.partnerSharing").value(false));

		// "Going OK?" an hour in. Ravi answers; Asha doesn't, so her trusted contact is alerted.
		clock.advance(Duration.ofMinutes(75));
		assertThat(dates.promptDueCheckIns()).isEqualTo(2);
		postAs(ravi, "/dates/" + dateId + "/check-in", "{\"needHelp\":false}").andExpect(jsonPath("$.ok").value(true));
		clock.advance(Duration.ofMinutes(16));
		assertThat(dates.escalateMissedCheckIns()).isEqualTo(1);
		deliverEvents();
		assertThat(sms.lastMessageTo(CONTACT))
			.hasValueSatisfying(m -> assertThat(m).contains("safety alert", "didn't answer a check-in", "112"));
		mvc.perform(get(sharePath)).andExpect(jsonPath("$.alert").value("MISSED_CHECK_IN"));
		// The other person on the date is never told.
		assertThat(body(getAs(ravi, "/dates/" + dateId))).doesNotContain("MISSED", "alert", "escalat");

		// Trust & Safety sees the alert with her last shared position, and resolves it (audited).
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		getAs(moderator, "/staff/date-alerts").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].reason").value("MISSED_CHECK_IN"))
			.andExpect(jsonPath("$[0].trustedContactSet").value(true))
			.andExpect(jsonPath("$[0].lastLat").value(12.9352));
		getAs(ravi, "/staff/date-alerts").andExpect(status().isForbidden());
		postAs(moderator, "/staff/date-alerts/" + dateId + "/" + userIdOf(asha) + "/resolve",
				"{\"note\":\"Reached her contact; all fine\"}")
			.andExpect(status().isNoContent());
		getAs(moderator, "/staff/date-alerts").andExpect(jsonPath("$", hasSize(0)));

		// End-of-date confirmation stops exact location for both people at once.
		postAs(asha, "/dates/" + dateId + "/end", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENDED"))
			.andExpect(jsonPath("$.you.homeSafe").value(true));
		position(asha, dateId).andExpect(status().isConflict());
		mvc.perform(get(sharePath)).andExpect(jsonPath("$.homeSafe").value(true));

		// A completed plan lands in both people's Real Value Ledger.
		deliverEvents();
		getAs(ravi, "/ledger").andExpect(jsonPath("$.datesCompleted").value(1))
			.andExpect(jsonPath("$.newConnections").value(1))
			.andExpect(jsonPath("$.lines[1]").value("1 plan you actually went to"));

		// Mutual Debrief: only overlapping, shareable answers, and only once both answered.
		debrief(asha, dateId, "HAD_A_GOOD_TIME", "WANT_TO_MEET_AGAIN", "FELT_SAFE", "FELT_RESPECTED")
			.andExpect(jsonPath("$.revealed").value(false))
			.andExpect(jsonPath("$.privateSafetyFollowUp").value(nullValue()));
		debrief(ravi, dateId, "HAD_A_GOOD_TIME", "GREAT_CONVERSATION")
			.andExpect(jsonPath("$.revealed").value(true))
			.andExpect(jsonPath("$.bothFelt", hasSize(1)))
			.andExpect(jsonPath("$.bothFelt[0]").value("You both had a good time"))
			.andExpect(jsonPath("$.privateSafetyFollowUp", containsString("safety team")));
		String ashaDebrief = body(getAs(asha, "/dates/" + dateId + "/debrief").andExpect(jsonPath("$.revealed").value(true)));
		assertThat(ashaDebrief).doesNotContain("conversation", "again", "safety team");
		debrief(ravi, dateId, "HAD_A_GOOD_TIME").andExpect(status().isConflict());

		// After the after-care window the contact's details and link are gone.
		clock.advance(Duration.ofHours(3));
		assertThat(dates.purgeClosedPlans()).isEqualTo(2);
		mvc.perform(get(sharePath)).andExpect(status().isNotFound());
		getAs(asha, "/dates/" + dateId).andExpect(jsonPath("$.you.trustedContact").value(nullValue()));
		assertThat(jdbc.queryForObject("select count(*) from date_participants where lat is not null "
				+ "or contact_phone is not null or share_token_hash is not null", Integer.class)).isZero();
	}

	@Test
	void sosIsPrivateAndABlockCancelsThePlan() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connectionId = connect(asha, ravi);
		Instant start = clock.instant().plus(Duration.ofHours(2));
		String dateId = JsonPath.read(body(propose(asha, connectionId, start, start.plus(Duration.ofHours(3)))), "$.id");
		postAs(ravi, "/dates/" + dateId + "/accept", null).andExpect(status().isOk());

		postAs(ravi, "/dates/" + dateId + "/sos", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("112")));
		clock.advance(Duration.ofHours(2));
		postAs(ravi, "/dates/" + dateId + "/sos", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.emergencyNumber").value("112"))
			.andExpect(jsonPath("$.trustedContactAlerted").value(false));
		// Asking twice doesn't raise a second alert.
		postAs(ravi, "/dates/" + dateId + "/check-in", "{\"needHelp\":true}").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'DateSafetyEscalated'",
				Integer.class)).isEqualTo(1);
		assertThat(body(getAs(asha, "/dates/" + dateId))).doesNotContain("SOS", "alert");
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		getAs(moderator, "/staff/date-alerts").andExpect(jsonPath("$[0].reason").value("SOS"));

		// Blocking cancels the plan for both, and the block itself stays invisible.
		postAs(ravi, "/safety/blocks", "{\"connectionId\":\"" + connectionId + "\"}").andExpect(status().isNoContent());
		getAs(asha, "/dates/" + dateId).andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.partnerLocation").value(nullValue()));
		deliverEvents();
		assertThat(jdbc.queryForObject("select status from date_plans where id = ?", String.class, dateId))
			.isEqualTo("CANCELLED");
		// SOS keeps working for the person who needs it, even after a block.
		postAs(ravi, "/dates/" + dateId + "/sos", null).andExpect(status().isOk());
	}

	@Test
	void plansNeedVerificationSanePlacesAndTimes() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connectionId = connect(asha, ravi);
		Instant start = clock.instant().plus(Duration.ofHours(1));

		propose(asha, connectionId, start, start.plus(Duration.ofHours(9))).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("INVALID_DURATION"));
		propose(asha, connectionId, start.plus(Duration.ofDays(40)), start.plus(Duration.ofDays(40)).plusSeconds(3600))
			.andExpect(jsonPath("$.code").value("INVALID_START"));
		postAs(asha, "/dates", "{\"connectionId\":\"" + connectionId + "\",\"startsAt\":\"" + start + "\",\"endsAt\":\""
				+ start.plusSeconds(3600) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PLACE_REQUIRED"));
		String stranger = verifiedUser("Stranger");
		propose(stranger, connectionId, start, start.plusSeconds(3600)).andExpect(status().isNotFound());
		String unverified = register("Unverified");
		propose(unverified, connectionId, start, start.plusSeconds(3600)).andExpect(status().isForbidden());

		// Safety-Verified Meeting Points: curated by staff, listed nearest first, usable in a plan.
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		postAs(asha, "/staff/meeting-points", meetingPoint("Nope", 12.93, 77.62)).andExpect(status().isForbidden());
		String near = JsonPath.read(body(postAs(moderator, "/staff/meeting-points",
				meetingPoint("Third Wave Coffee, Koramangala", 12.9345, 77.6260)).andExpect(status().isCreated())), "$.id");
		postAs(moderator, "/staff/meeting-points", meetingPoint("Cubbon Park Gate", 12.9763, 77.5929))
			.andExpect(status().isCreated());
		postAs(moderator, "/staff/meeting-points", meetingPoint("Mysuru Palace", 12.3052, 76.6552))
			.andExpect(status().isCreated());
		getAs(asha, "/meeting-points?radiusKm=15").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].id").value(near))
			.andExpect(jsonPath("$[0].distanceKm", notNullValue()));
		String plan = body(postAs(asha, "/dates", "{\"connectionId\":\"" + connectionId + "\",\"meetingPointId\":\"" + near
				+ "\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + start.plusSeconds(5400) + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.placeName").value("Third Wave Coffee, Koramangala"))
			.andExpect(jsonPath("$.meetingPoint.category").value("CAFE")));

		// An unanswered proposal quietly expires once its start time passes.
		clock.advance(Duration.ofHours(2));
		assertThat(dates.closeFinishedPlans()).isEqualTo(1);
		getAs(ravi, "/dates/" + JsonPath.read(plan, "$.id")).andExpect(jsonPath("$.status").value("EXPIRED"));
	}

	private ResultActions propose(String token, String connectionId, Instant startsAt, Instant endsAt) throws Exception {
		return postAs(token, "/dates", """
				{"connectionId":"%s","placeName":"Third Wave Coffee, Koramangala","startsAt":"%s","endsAt":"%s"}
				""".formatted(connectionId, startsAt, endsAt));
	}

	private void share(String token, String dateId, boolean enabled) throws Exception {
		perform(put("/dates/" + dateId + "/sharing"), token, "{\"enabled\":" + enabled + "}").andExpect(status().isOk());
	}

	private ResultActions position(String token, String dateId) throws Exception {
		return perform(put("/dates/" + dateId + "/location"), token, "{\"lat\":12.9352,\"lon\":77.6245}");
	}

	private ResultActions debrief(String token, String dateId, String... answers) throws Exception {
		return postAs(token, "/dates/" + dateId + "/debrief",
				"{\"answers\":[\"" + String.join("\",\"", answers) + "\"]}");
	}

	private static String meetingPoint(String name, double lat, double lon) {
		return """
				{"name":"%s","category":"CAFE","address":"Bengaluru","lat":%s,"lon":%s,"safetyNotes":"Staffed till 11pm"}
				""".formatted(name, lat, lon);
	}
}
