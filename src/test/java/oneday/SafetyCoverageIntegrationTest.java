package oneday;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;

/** Every surface where people meet can block and report: plans, Room messages, Right Now and dates. */
class SafetyCoverageIntegrationTest extends ApiTestSupport {

	@Test
	void plansAndRoomsCanBeBlockedAndReportedFromWherePeopleMeet() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String venue = JsonPath.read(body(postAs(moderator, "/staff/meeting-points",
				"{\"name\":\"Bandstand\",\"category\":\"PARK\",\"address\":\"Bengaluru\",\"lat\":12.9360,\"lon\":77.6250}")),
				"$.id");
		String host = located("Asha");
		String priya = located("Priya");
		String ravi = located("Ravi");
		Instant start = clock.instant().plus(Duration.ofHours(2));
		String plan = JsonPath.read(body(postAs(host, "/plans", """
				{"meetingPointId":"%s","activity":"chess","capacity":4,"startsAt":"%s","endsAt":"%s"}
				""".formatted(venue, start, start.plus(Duration.ofHours(2))))), "$.id");
		postAs(priya, "/plans/" + plan + "/join", null).andExpect(status().isOk());
		List<String> handles = JsonPath.read(body(getAs(host, "/plans/" + plan + "/requests")), "$[*].handle");
		postAs(host, "/plans/" + plan + "/requests/" + handles.get(0) + "/approve", null).andExpect(status().isOk());
		String roomMessage = JsonPath.read(body(postAs(host, "/plans/" + plan + "/room", "{\"body\":\"hi all\"}")), "$.id");

		// A member reports a Room message; an outsider can't even name it.
		postAs(priya, "/safety/reports", "{\"roomMessageId\":\"" + roomMessage + "\",\"category\":\"HARASSMENT\"}")
			.andExpect(status().isCreated());
		postAs(ravi, "/safety/reports", "{\"roomMessageId\":\"" + roomMessage + "\",\"category\":\"HARASSMENT\"}")
			.andExpect(status().isNotFound());
		getAs(moderator, "/staff/reports").andExpect(jsonPath("$[0].targetType").value("ROOM_MESSAGE"));

		// Blocking a host from the plan card hides their plans.
		getAs(ravi, "/plans").andExpect(jsonPath("$", hasSize(1)));
		postAs(ravi, "/safety/blocks", "{\"planId\":\"" + plan + "\"}").andExpect(status().isNoContent());
		getAs(ravi, "/plans").andExpect(jsonPath("$", hasSize(0)));
		postAs(host, "/safety/blocks", "{\"planId\":\"" + plan + "\"}").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("CANNOT_TARGET_SELF"));

		postAs(ravi, "/safety/blocks", "{\"planId\":\"" + plan + "\",\"momentId\":\"x\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("rightNowId")));
		postAs(ravi, "/safety/reports", "{\"planId\":\"" + plan + "\",\"category\":\"NOPE\"}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void rightNowAndDatesAreCoveredToo() throws Exception {
		String asha = located("Asha");
		String ravi = located("Ravi");
		postAs(asha, "/right-now", "{\"activity\":\"badminton\",\"minutes\":60}").andExpect(status().isCreated());
		String session = JsonPath.read(body(getAs(ravi, "/right-now")), "$[0].id");
		postAs(ravi, "/safety/blocks", "{\"rightNowId\":\"" + session + "\"}").andExpect(status().isNoContent());
		getAs(ravi, "/right-now").andExpect(jsonPath("$", hasSize(0)));

		String meera = located("Meera");
		String connection = connect(meera, asha);
		Instant start = clock.instant().plus(Duration.ofHours(2));
		String date = JsonPath.read(body(postAs(meera, "/dates", """
				{"connectionId":"%s","placeName":"Cafe","startsAt":"%s","endsAt":"%s"}
				""".formatted(connection, start, start.plus(Duration.ofHours(2))))), "$.id");
		postAs(asha, "/safety/reports",
				"{\"dateId\":\"" + date + "\",\"category\":\"THREAT_OR_VIOLENCE\",\"alsoBlock\":true}")
			.andExpect(status().isCreated());
		postAs(ravi, "/safety/reports", "{\"dateId\":\"" + date + "\",\"category\":\"SPAM\"}").andExpect(status().isNotFound());
		getAs(meera, "/dates/" + date).andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	private String located(String name) throws Exception {
		String token = verifiedUser(name);
		locate(token, BLR_LAT, BLR_LON);
		return token;
	}
}
