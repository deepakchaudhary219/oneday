package oneday;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Pacing Guardian: one-sided pressure is nudged, then paused; flooding is slowed; normal chat never notices. */
class PacingGuardianIntegrationTest extends ApiTestSupport {

	@Test
	void unansweredMessagesAreNudgedThenPausedUntilAReply() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		connect(asha, ravi);
		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");

		say(ravi, conversation).andExpect(status().isCreated()).andExpect(jsonPath("$.pacingHint").value(nullValue()));
		say(ravi, conversation).andExpect(jsonPath("$.pacingHint").value(nullValue()));
		say(ravi, conversation).andExpect(jsonPath("$.pacingHint", containsString("maybe wait for a reply")));
		say(ravi, conversation).andExpect(status().isCreated());
		say(ravi, conversation).andExpect(status().isCreated());
		say(ravi, conversation).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("PACING_PAUSE"));
		// The other person sees ordinary messages; the hint was only ever for the sender.
		getAs(asha, "/conversations/" + conversation + "/messages")
			.andExpect(jsonPath("$[0].pacingHint").value(nullValue()));

		// A reply resets it.
		say(asha, conversation).andExpect(status().isCreated());
		say(ravi, conversation).andExpect(status().isCreated()).andExpect(jsonPath("$.pacingHint").value(nullValue()));

		// So does a day passing.
		clock.advance(Duration.ofMinutes(2)); // past the per-minute burst window
		for (int i = 0; i < 4; i++) {
			say(ravi, conversation);
		}
		say(ravi, conversation).andExpect(jsonPath("$.code").value("PACING_PAUSE"));
		clock.advance(Duration.ofHours(25));
		say(ravi, conversation).andExpect(status().isCreated());
	}

	@Test
	void floodingIsSlowedEvenInABackAndForth() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		connect(asha, ravi);
		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");
		for (int i = 0; i < 10; i++) {
			say(asha, conversation).andExpect(status().isCreated());
			say(ravi, conversation).andExpect(status().isCreated());
		}
		say(asha, conversation).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("PACING_SLOW_DOWN"));
		clock.advance(Duration.ofMinutes(2));
		say(asha, conversation).andExpect(status().isCreated());
	}

	/** One second apart, like real typing: messages at an identical instant would have no defined order. */
	private ResultActions say(String token, String conversation) throws Exception {
		clock.advance(Duration.ofSeconds(1));
		return postAs(token, "/conversations/" + conversation + "/messages", "{\"body\":\"hey\"}");
	}
}
