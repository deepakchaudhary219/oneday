package oneday;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** AMA Corridors: a verified figure answers one Roots corridor; votes order questions but stay private. */
@SpringBootTest
class AmaCorridorIntegrationTest extends ApiTestSupport {

	@Test
	void aCorridorAmaIsForItsRegionAndVotesStayPrivate() throws Exception {
		String host = figure();
		String ravi = verifiedUser("Ravi");
		String anu = verifiedUser("Anu");
		String meera = verifiedUser("Meera");
		updateProfile(ravi, "{\"homeRegion\":\"IN-KL\"}");
		updateProfile(anu, "{\"homeRegion\":\"IN-KL\"}");
		updateProfile(meera, "{\"homeRegion\":\"IN-WB\"}");

		postAs(ravi, "/amas", schedule(clock.instant().plus(Duration.ofHours(2)))).andExpect(status().isForbidden());
		String ama = JsonPath.read(body(postAs(host, "/amas", schedule(clock.instant().plus(Duration.ofHours(2))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.hostHandle").value("kavya.sings"))
			.andExpect(jsonPath("$.state").value("UPCOMING"))), "$.id");
		getAs(ravi, "/amas").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].canAsk").value(true));
		getAs(meera, "/amas").andExpect(jsonPath("$", hasSize(0)));
		getAs(meera, "/amas/" + ama).andExpect(status().isNotFound());

		String q1 = ask(ravi, ama, "How do you keep Malayalam alive away from home?", false);
		String q2 = ask(anu, ama, "Which Onam song do you sing first?", true);
		postAs(anu, "/amas/" + ama + "/questions", "{\"question\":\"you're so stupid\"}")
			.andExpect(status().isUnprocessableContent());
		postAs(ravi, "/amas/" + ama + "/questions/" + q2 + "/vote", null).andExpect(status().isNoContent());
		postAs(anu, "/amas/" + ama + "/questions/" + q2 + "/vote", null).andExpect(status().isNoContent());

		getAs(ravi, "/amas/" + ama).andExpect(jsonPath("$.questions[0].id").value(q2))
			.andExpect(jsonPath("$.questions[0].askedBy").value("Someone from IN-KL"))
			.andExpect(jsonPath("$.questions[0].youVoted").value(true))
			.andExpect(jsonPath("$.questions[0].votes").doesNotExist())
			.andExpect(jsonPath("$.questions[1].askedBy").value("Ravi"));
		getAs(host, "/amas/" + ama).andExpect(jsonPath("$.questions[0].votes").value(2));

		postAs(host, "/amas/" + ama + "/questions/" + q1 + "/answer", "{\"answer\":\"Songs\"}")
			.andExpect(status().isConflict()); // not live yet
		clock.advance(Duration.ofHours(2).plusMinutes(1));
		host = signIn(host);
		ravi = signIn(ravi);
		postAs(host, "/amas/" + ama + "/questions/" + q1 + "/answer", "{\"answer\":\"Bedtime stories in Malayalam.\"}")
			.andExpect(jsonPath("$.answer").value("Bedtime stories in Malayalam."));
		getAs(ravi, "/amas/" + ama).andExpect(jsonPath("$.ama.state").value("LIVE"))
			.andExpect(jsonPath("$.questions[0].id").value(q1)); // answered first

		postAs(ravi, "/amas/" + ama + "/questions/" + q2 + "/hide", null).andExpect(status().isForbidden());
		postAs(host, "/amas/" + ama + "/questions/" + q2 + "/hide", null).andExpect(status().isNoContent());
		getAs(ravi, "/amas/" + ama).andExpect(jsonPath("$.questions", hasSize(1)));
		postAs(ravi, "/safety/reports", "{\"amaQuestionId\":\"" + q1 + "\",\"category\":\"SPAM\"}")
			.andExpect(status().isUnprocessableContent()); // can't report yourself
		postAs(host, "/safety/reports", "{\"amaQuestionId\":\"" + q1 + "\",\"category\":\"SPAM\"}")
			.andExpect(status().isCreated());
	}

	private String figure() throws Exception {
		String kavya = verifiedUser("Kavya");
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		postAs(kavya, "/figures/apply", """
				{"handle":"kavya.sings","publicName":"Kavya Menon","category":"MUSICIAN","evidence":"https://example.org"}""")
			.andExpect(status().isCreated());
		postAs(moderator, "/staff/figures/" + userIdOf(kavya) + "/decision", "{\"decision\":\"APPROVE\"}")
			.andExpect(status().isOk());
		return kavya;
	}

	private static String schedule(Instant startsAt) {
		return "{\"title\":\"Malayalam songs abroad\",\"corridorRegion\":\"in-kl\",\"startsAt\":\"%s\",\"minutes\":60}"
			.formatted(startsAt);
	}

	private String ask(String token, String ama, String question, boolean anonymous) throws Exception {
		clock.advance(Duration.ofSeconds(1));
		return JsonPath.read(body(postAs(token, "/amas/" + ama + "/questions",
				"{\"question\":\"%s\",\"anonymous\":%s}".formatted(question, anonymous))
			.andExpect(status().isCreated())), "$.id");
	}
}
