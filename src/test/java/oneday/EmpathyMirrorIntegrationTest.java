package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The Empathy Mirror: a reflection before an unkind message, "send anyway" always possible, and the recipient
 * asked "does this bother you?" with the matching report one tap away.
 */
@SpringBootTest
class EmpathyMirrorIntegrationTest extends ApiTestSupport {

	@Autowired
	MeterRegistry metrics;

	@Test
	void anUnkindChatMessageIsReflectedBackAndTheRecipientIsAskedIfItBothersThem() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connection = connect(asha, ravi);
		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");
		String messages = "/conversations/" + conversation + "/messages";
		double prompted = count("chat", "prompted");

		postAs(asha, messages, "{\"body\":\"this stupid rain, chai instead?\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.concern").doesNotExist());
		postAs(asha, messages, "{\"body\":\"you're so stupid\"}").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("EMPATHY_CHECK"))
			.andExpect(jsonPath("$.tone").value("INSULT"))
			.andExpect(jsonPath("$.detail", containsString("kinder")));
		getAs(ravi, messages).andExpect(jsonPath("$", hasSize(1))); // nothing was sent
		assertThat(count("chat", "prompted")).isEqualTo(prompted + 1);

		clock.advance(Duration.ofSeconds(5));
		postAs(asha, messages, "{\"body\":\"you're so stupid\",\"sendAnyway\":true}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.concern").doesNotExist()); // the sender is never shown the recipient's prompt
		getAs(ravi, messages).andExpect(jsonPath("$[0].body").value("you're so stupid"))
			.andExpect(jsonPath("$[0].concern.question").value("Does this bother you?"))
			.andExpect(jsonPath("$[0].concern.reportCategory").value("HARASSMENT"))
			.andExpect(jsonPath("$[1].concern").doesNotExist());
		getAs(asha, messages).andExpect(jsonPath("$[0].concern").doesNotExist());
		assertThat(count("chat", "sent_anyway")).isGreaterThanOrEqualTo(1);

		postAs(ravi, "/safety/reports", "{\"connectionId\":\"" + connection + "\",\"category\":\"HARASSMENT\"}")
			.andExpect(status().isCreated());
	}

	@Test
	void roomMessagesGetTheSameMirror() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String venue = JsonPath.read(body(postAs(moderator, "/staff/meeting-points",
				"{\"name\":\"Bandstand\",\"category\":\"PARK\",\"address\":\"Bengaluru\",\"lat\":12.9360,\"lon\":77.6250}")),
				"$.id");
		String host = located("Asha");
		String priya = located("Priya");
		Instant start = clock.instant().plus(Duration.ofHours(2));
		String plan = JsonPath.read(body(postAs(host, "/plans", """
				{"meetingPointId":"%s","activity":"chess","capacity":4,"startsAt":"%s","endsAt":"%s"}
				""".formatted(venue, start, start.plus(Duration.ofHours(2))))), "$.id");
		postAs(priya, "/plans/" + plan + "/join", null).andExpect(status().isOk());
		List<String> handles = JsonPath.read(body(getAs(host, "/plans/" + plan + "/requests")), "$[*].handle");
		postAs(host, "/plans/" + plan + "/requests/" + handles.get(0) + "/approve", null).andExpect(status().isOk());
		String room = "/plans/" + plan + "/room";

		postAs(priya, room, "{\"body\":\"bring the board or I'll find where you live\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.tone").value("THREAT"));
		postAs(priya, room, "{\"body\":\"bring the board or I'll find where you live\",\"sendAnyway\":true}")
			.andExpect(status().isCreated());
		getAs(host, room).andExpect(jsonPath("$[0].concern.reportCategory").value("THREAT_OR_VIOLENCE"));
		getAs(priya, room).andExpect(jsonPath("$[0].concern").doesNotExist());
	}

	private double count(String surface, String outcome) {
		return metrics.counter("oneday.empathy_mirror", "surface", surface, "outcome", outcome).count();
	}

	private String located(String name) throws Exception {
		String token = verifiedUser(name);
		locate(token, BLR_LAT, BLR_LON);
		return token;
	}
}
