package oneday;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Spotlight Replies: the owner asks, the answerer decides, and only then does the relay show it first. */
@SpringBootTest
class SpotlightIntegrationTest extends ApiTestSupport {

	@Test
	void aSpotlightNeedsTheAnswerersYesAndLeadsTheRelay() throws Exception {
		String asha = located("Asha");
		String ravi = located("Ravi");
		String meera = located("Meera");
		String story = postPublicMoment(asha, "trek");
		String first = answer(meera, story, "Nandi Hills at dawn");
		clock.advance(Duration.ofSeconds(2));
		String second = answer(ravi, story, "Skandagiri!");

		postAs(ravi, "/moments/" + story + "/spotlights", "{\"answerMomentId\":\"" + second + "\"}")
			.andExpect(status().isNotFound()); // only the story's owner
		postAs(asha, "/moments/" + story + "/spotlights", "{\"answerMomentId\":\"" + story + "\"}")
			.andExpect(status().isNotFound());
		String spotlight = JsonPath.read(body(postAs(asha, "/moments/" + story + "/spotlights",
				"{\"answerMomentId\":\"" + second + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))), "$.id");
		postAs(asha, "/moments/" + story + "/spotlights", "{\"answerMomentId\":\"" + second + "\"}")
			.andExpect(status().isConflict());

		// Not shown until Ravi says yes.
		getAs(meera, "/moments/" + story + "/relay").andExpect(jsonPath("$.links[?(@.spotlit==true)]", hasSize(0)));
		getAs(ravi, "/spotlights/requests").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].withFirstName").value("Asha"));
		postAs(meera, "/spotlights/" + spotlight + "/accept", null).andExpect(status().isNotFound());
		postAs(ravi, "/spotlights/" + spotlight + "/accept", null).andExpect(jsonPath("$.status").value("ACCEPTED"));
		getAs(meera, "/moments/" + story + "/relay").andExpect(jsonPath("$.links[0].momentId").value(second))
			.andExpect(jsonPath("$.links[0].spotlit").value(true))
			.andExpect(jsonPath("$.links[?(@.momentId=='" + first + "')].spotlit").value(contains(false)));

		// The answerer can take it back.
		deleteAs(ravi, "/spotlights/" + spotlight).andExpect(status().isNoContent());
		getAs(meera, "/moments/" + story + "/relay").andExpect(jsonPath("$.links[?(@.spotlit==true)]", hasSize(0)));
	}

	private String answer(String token, String story, String caption) throws Exception {
		return JsonPath.read(body(postAs(token, "/moments", """
				{"kind":"TEXT","caption":"%s","activityTag":"trek","shareScope":"PUBLIC_DISCOVERY","replyToMomentId":"%s"}
				""".formatted(caption, story)).andExpect(status().isCreated())), "$.id");
	}

	private String located(String name) throws Exception {
		String token = verifiedUser(name);
		locate(token, BLR_LAT, BLR_LON);
		return token;
	}
}
