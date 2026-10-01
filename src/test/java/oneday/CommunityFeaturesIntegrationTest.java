package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.plans.PlanService;
import oneday.privacy.PrivacyService;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** Plans &amp; Rooms, Trusted Vouch and the Memory Trail. */
class CommunityFeaturesIntegrationTest extends ApiTestSupport {

	@Autowired
	private PlanService planService;

	@Autowired
	private PrivacyService privacy;

	@Test
	void plansAreHostApprovedMeetUpsWithATemporaryRoom() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String venue = JsonPath.read(body(postAs(moderator, "/staff/meeting-points", """
				{"name":"Cubbon Park Bandstand","category":"PARK","address":"Bengaluru","lat":12.9360,"lon":77.6250}
				""").andExpect(status().isCreated())), "$.id");
		String host = located("Asha", "{\"homeRegion\":\"IN-KL\"}");
		String priya = located("Priya", null);
		String ravi = located("Ravi", null);
		String dev = located("Dev", null);
		Instant start = clock.instant().plus(Duration.ofHours(3));

		host(host, venue, 2, start, false).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("INVALID_CAPACITY"));
		String planId = JsonPath.read(body(host(host, venue, 3, start, false).andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("chess at Cubbon Park Bandstand"))
			.andExpect(jsonPath("$.spotsLeft").value(2))
			.andExpect(jsonPath("$.going", hasSize(1)))), "$.id");

		String nearby = body(getAs(priya, "/plans").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].hostFirstName").value("Asha"))
			.andExpect(jsonPath("$[0].place").value("Cubbon Park Bandstand"))
			.andExpect(jsonPath("$[0].you").value(nullValue())));
		assertThat(nearby).doesNotContain(userIdOf(host));

		// Joining is a request; the host approves. A decline is silent.
		postAs(priya, "/plans/" + planId + "/join", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.yourStatus").value("REQUESTED"))
			.andExpect(jsonPath("$.going", hasSize(0)));
		postAs(priya, "/plans/" + planId + "/join", null).andExpect(status().isConflict());
		postAs(ravi, "/plans/" + planId + "/join", null).andExpect(status().isOk());
		String requests = body(getAs(host, "/plans/" + planId + "/requests").andExpect(jsonPath("$", hasSize(2))));
		assertThat(requests).doesNotContain(userIdOf(priya), userIdOf(ravi));
		getAs(priya, "/plans/" + planId + "/requests").andExpect(status().isNotFound());
		List<String> priyaHandle = JsonPath.read(requests, "$[?(@.firstName=='Priya')].handle");
		List<String> raviHandle = JsonPath.read(requests, "$[?(@.firstName=='Ravi')].handle");
		postAs(host, "/plans/" + planId + "/requests/" + priyaHandle.get(0) + "/approve", null)
			.andExpect(jsonPath("$.spotsLeft").value(1));
		postAs(host, "/plans/" + planId + "/requests/" + raviHandle.get(0) + "/decline", null).andExpect(status().isOk());
		getAs(ravi, "/plans/" + planId).andExpect(jsonPath("$.yourStatus").value("REQUESTED"))
			.andExpect(jsonPath("$.going", hasSize(0)));
		getAs(priya, "/plans/" + planId).andExpect(jsonPath("$.yourStatus").value("APPROVED"))
			.andExpect(jsonPath("$.going", containsInAnyOrder("Asha", "Priya")));

		// The Room is for members only.
		postAs(priya, "/plans/" + planId + "/room", "{\"body\":\"See you at the bandstand!\"}")
			.andExpect(status().isCreated());
		getAs(host, "/plans/" + planId + "/room").andExpect(jsonPath("$[0].firstName").value("Priya"))
			.andExpect(jsonPath("$[0].mine").value(false));
		getAs(ravi, "/plans/" + planId + "/room").andExpect(status().isNotFound());

		// Blocks hide plans both ways: Dev blocked a member, so the plan is invisible to him.
		String priyaMoment = JsonPath.read(body(postAs(priya, "/moments",
				"{\"kind\":\"TEXT\",\"caption\":\"hi\",\"shareScope\":\"PUBLIC_DISCOVERY\"}")), "$.id");
		postAs(dev, "/safety/blocks", "{\"momentId\":\"" + priyaMoment + "\"}").andExpect(status().isNoContent());
		getAs(dev, "/plans").andExpect(jsonPath("$", hasSize(0)));
		postAs(dev, "/plans/" + planId + "/join", null).andExpect(status().isNotFound());

		// A Roots plan is visible only to people from the same home region.
		host(host, venue, 6, start.plus(Duration.ofDays(1)), true).andExpect(status().isCreated())
			.andExpect(jsonPath("$.rootsPlan").value(true));
		getAs(ravi, "/plans").andExpect(jsonPath("$", hasSize(1)));
		updateProfile(ravi, "{\"homeRegion\":\"IN-KL\"}");
		getAs(ravi, "/plans").andExpect(jsonPath("$", hasSize(2)));

		// An approval is a meaningful two-way moment; the Room is deleted a day after the plan.
		deliverEvents();
		assertThat(jdbc.queryForObject("select count(*) from weekly_actives", Integer.class)).isEqualTo(2);
		clock.advance(Duration.ofDays(3));
		planService.sweep();
		assertThat(jdbc.queryForObject("select count(*) from plan_messages", Integer.class)).isZero();
		getAs(priya, "/plans/" + planId).andExpect(status().isNotFound());
	}

	@Test
	void vouchesComeFromRealContactAndShowOnlyAsACount() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connectionId = connect(asha, ravi);
		postAs(ravi, "/connections/" + connectionId + "/vouch", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TOO_SOON"));

		clock.advance(Duration.ofDays(15));
		postAs(ravi, "/connections/" + connectionId + "/vouch", null)
			.andExpect(jsonPath("$.code").value("NOT_ENOUGH_CONTACT"));
		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");
		for (int day = 0; day < 2; day++) {
			postAs(asha, "/conversations/" + conversation + "/messages", "{\"body\":\"hey\"}").andExpect(status().isCreated());
			postAs(ravi, "/conversations/" + conversation + "/messages", "{\"body\":\"hi!\"}").andExpect(status().isCreated());
			clock.advance(Duration.ofDays(1));
		}
		postAs(ravi, "/connections/" + connectionId + "/vouch", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.youVouch").value(true));
		getAs(asha, "/vouches/mine").andExpect(jsonPath("$.received").value("1"));
		getAs(ravi, "/vouches/mine").andExpect(jsonPath("$.given[0]").value("Asha"));

		// A stranger sees "vouched by 1", never by whom.
		String meera = verifiedUser("Meera");
		locate(meera, BLR_LAT, BLR_LON);
		postAs(asha, "/moments", "{\"kind\":\"TEXT\",\"caption\":\"chai?\",\"activityTag\":\"chai\",\"shareScope\":\"PUBLIC_DISCOVERY\"}")
			.andExpect(status().isCreated());
		String constellation = body(getAs(meera, "/discover/constellation").andExpect(jsonPath("$.nodes[0].firstName").value("Asha"))
			.andExpect(jsonPath("$.nodes[0].vouchedBy").value("1")));
		assertThat(constellation).doesNotContain("Ravi", userIdOf(ravi));

		// A block removes vouches both ways.
		postAs(asha, "/safety/blocks", "{\"connectionId\":\"" + connectionId + "\"}").andExpect(status().isNoContent());
		deliverEvents();
		getAs(meera, "/discover/constellation").andExpect(jsonPath("$.nodes[0].vouchedBy").value(nullValue()));
	}

	@Test
	void theMemoryTrailKeepsChosenStoriesPrivatelyWithOnlyTheirArea() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);
		String kept = postPublicMoment(asha, "trek");
		String other = postPublicMoment(asha, "coffee");
		postAs(asha, "/moments/" + kept + "/keep", null).andExpect(status().isOk());
		postAs(ravi, "/moments/" + other + "/keep", null).andExpect(status().isNotFound());
		deliverEvents(); // media copied out of the expiring prefix, off the request path
		String trailRef = jdbc.queryForObject("select trail_media_ref from moments where id = ?", String.class, kept);
		assertThat(trailRef).startsWith("trail/");

		clock.advance(Duration.ofDays(16));
		privacy.purgeExpiredMoments();
		assertThat(jdbc.queryForObject("select count(*) from moments where id = ?", Integer.class, other)).isZero();
		assertThat(jdbc.queryForObject("select cell from moments where id = ?", String.class, kept)).hasSize(5);
		asha = signIn(asha);
		String trail = body(getAs(asha, "/moments/trail").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].activity").value("trek"))
			.andExpect(jsonPath("$[0].mediaUrl", containsString("trail/")))
			.andExpect(jsonPath("$[0].areaLat").isNumber()));
		assertThat(trail).doesNotContain("12.9352");
		getAs(signIn(ravi), "/moments/" + kept).andExpect(status().isNotFound()); // private forever

		deleteAs(asha, "/moments/" + kept + "/keep").andExpect(status().isNoContent());
		getAs(asha, "/moments/trail").andExpect(jsonPath("$", hasSize(0)));
	}

	private String located(String name, String profile) throws Exception {
		String token = verifiedUser(name);
		if (profile != null) {
			updateProfile(token, profile);
		}
		locate(token, BLR_LAT, BLR_LON);
		return token;
	}

	private ResultActions host(String token, String venue, int capacity, Instant start, boolean roots) throws Exception {
		return postAs(token, "/plans", """
				{"meetingPointId":"%s","activity":"Chess","capacity":%d,"startsAt":"%s","endsAt":"%s","rootsOnly":%s}
				""".formatted(venue, capacity, start, start.plus(Duration.ofHours(2)), roots));
	}
}
