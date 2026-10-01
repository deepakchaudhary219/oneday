package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The v1 core loop end to end: live story → nearby discovery (Layer 0) → Signal (Layer 1) → digest →
 * Mutual Reveal (Layer 2) → Friend-Mode chat → Mutual Spark → block.
 */
class CoreLoopIntegrationTest extends ApiTestSupport {

	private static final String KERALA_PROFILE = """
			{"activities":["Trek","filter coffee"],"values":["KINDNESS","ADVENTURE"],"languages":["ml","en"],
			 "homeRegion":"in-kl","datingLens":true,"gender":"%s","interestedIn":["%s"]}
			""";

	@Test
	void strangerToConnectionToMutualSpark() throws Exception {
		String asha = verifiedUser("Asha Nair");
		String ravi = verifiedUser("Ravi Menon");
		updateProfile(asha, KERALA_PROFILE.formatted("WOMAN", "MAN"));
		updateProfile(ravi, KERALA_PROFILE.formatted("MAN", "WOMAN"));
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);

		String momentId = postPublicMoment(asha, "trek");

		// Layer 0: Ravi sees an ambient node: first name, activity, band, shared context. No ids or coordinates.
		String constellation = body(getAs(ravi, "/discover/constellation").andExpect(status().isOk())
			.andExpect(jsonPath("$.nodes", hasSize(1)))
			.andExpect(jsonPath("$.nodes[0].nodeId").value(momentId))
			.andExpect(jsonPath("$.nodes[0].firstName").value("Asha"))
			.andExpect(jsonPath("$.nodes[0].activity").value("trek"))
			.andExpect(jsonPath("$.nodes[0].band").value("UNDER_1_KM"))
			.andExpect(jsonPath("$.nodes[0].sharedHomeRegion").value("IN-KL"))
			.andExpect(jsonPath("$.nodes[0].whyYouSeeThis", containsString("kindness and adventure")))
			.andExpect(jsonPath("$.caughtUp").value(true)));
		assertThat(constellation).doesNotContain(userIdOf(asha), "12.93", "77.62", "Sunrise", "moments/");

		getAs(ravi, "/moments/" + momentId).andExpect(status().isOk())
			.andExpect(jsonPath("$.layer").value("AMBIENT"))
			.andExpect(jsonPath("$.caption").value(nullValue()))
			.andExpect(jsonPath("$.mediaUrl").value(nullValue()));

		// Layer 1: a signal with an activity reference, never free text. One per moment.
		String signalId = sendSignal(ravi, momentId, "trek");
		postAs(ravi, "/signals", "{\"momentId\":\"" + momentId + "\",\"reaction\":\"SAME_HERE\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_SIGNALLED"));

		// Sender's Consent Trail never reveals seen/ignored.
		String sent = body(getAs(ravi, "/signals/sent").andExpect(jsonPath("$[0].becameConnection").value(false)));
		assertThat(sent).doesNotContain("status", "PENDING", "windowExpiresAt");

		// Recipient's digest: bounded, no countdown.
		String digest = body(getAs(asha, "/signals/digest").andExpect(status().isOk())
			.andExpect(jsonPath("$.signals", hasSize(1)))
			.andExpect(jsonPath("$.signals[0].firstName").value("Ravi"))
			.andExpect(jsonPath("$.signals[0].activityRef").value("trek"))
			.andExpect(jsonPath("$.signals[0].rootsMatch").value(true))
			.andExpect(jsonPath("$.caughtUp").value(true)));
		assertThat(digest).doesNotContain("expires", "rank", userIdOf(ravi));
		getAs(asha, "/pulse").andExpect(jsonPath("$.pendingSignals").value("1"))
			.andExpect(jsonPath("$.headline").value("1 person sent you a signal"));

		// Layer 2: Mutual Reveal creates the connection and a seeded conversation.
		String reveal = body(postAs(asha, "/signals/" + signalId + "/reveal", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.seedContext").value("Connected over: trek")));
		String connectionId = JsonPath.read(reveal, "$.connectionId");
		String conversationId = JsonPath.read(reveal, "$.conversationId");

		getAs(ravi, "/moments/" + momentId).andExpect(jsonPath("$.layer").value("FULL"))
			.andExpect(jsonPath("$.caption").value("Sunrise at Nandi Hills"))
			.andExpect(jsonPath("$.mediaUrl", org.hamcrest.Matchers.startsWith(DEV_MEDIA + "moments/")));
		getAs(ravi, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(0)));
		getAs(ravi, "/signals/sent").andExpect(jsonPath("$[0].becameConnection").value(true));

		// Friend Mode chat.
		postAs(ravi, "/conversations/" + conversationId + "/messages", "{\"body\":\"Which trail were you on?\"}")
			.andExpect(status().isCreated());
		getAs(asha, "/conversations/" + conversationId + "/messages").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].mine").value(false))
			.andExpect(jsonPath("$[0].body").value("Which trail were you on?"));
		getAs(asha, "/conversations/" + conversationId + "/balance").andExpect(jsonPath("$.state").value("EARLY"));

		// Mutual Spark: a one-sided spark is invisible to the other person.
		postAs(ravi, "/connections/" + connectionId + "/spark", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.youSparked").value(true))
			.andExpect(jsonPath("$.mutualSpark").value(false));
		String ashaView = body(getAs(asha, "/connections").andExpect(jsonPath("$[0].mutualSpark").value(false)));
		assertThat(ashaView).doesNotContain("youSparked", "sparkA", "sparkB");
		postAs(asha, "/connections/" + connectionId + "/spark", null)
			.andExpect(jsonPath("$.mutualSpark").value(true))
			.andExpect(jsonPath("$.message").value("You both sparked ✨"));
		getAs(ravi, "/connections").andExpect(jsonPath("$[0].mutualSpark").value(true))
			.andExpect(jsonPath("$[0].displayName").value("Asha Nair"));

		// Block propagates: connection ends, chat closes, both vanish from each other's lists.
		postAs(asha, "/safety/blocks", "{\"connectionId\":\"" + connectionId + "\"}")
			.andExpect(status().isNoContent());
		postAs(ravi, "/conversations/" + conversationId + "/messages", "{\"body\":\"hello?\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONVERSATION_INACTIVE"));
		getAs(ravi, "/connections").andExpect(jsonPath("$", hasSize(0)));
		getAs(ravi, "/moments/" + momentId).andExpect(status().isNotFound());
	}

	@Test
	void browsingIsOpenButEveryContactActionNeedsVerification() throws Exception {
		String newcomer = register("Meera Iyer");
		locate(newcomer, BLR_LAT, BLR_LON);
		getAs(newcomer, "/discover/constellation").andExpect(status().isOk());
		getAs(newcomer, "/profile/me").andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));

		String momentJson = """
				{"kind":"TEXT","caption":"hi","shareScope":"PUBLIC_DISCOVERY","capturedLive":true}""";
		postAs(newcomer, "/moments", momentJson).andExpect(status().isForbidden());
		postAs(newcomer, "/signals", "{\"momentId\":\"x\",\"reaction\":\"SAME_HERE\"}")
			.andExpect(status().isForbidden());
		postAs(newcomer, "/signals/x/reveal", null).andExpect(status().isForbidden());
		postAs(newcomer, "/conversations/x/messages", "{\"body\":\"hi\"}").andExpect(status().isForbidden());

		String verified = verify(newcomer, "dev-pass");
		postAs(verified, "/moments", momentJson).andExpect(status().isCreated());
	}

	@Test
	void doubtfulLivenessGoesToManualReviewAndKeepsContactLocked() throws Exception {
		String token = register("Kabir Das");
		String response = body(postAs(token, "/verification/liveness", "{\"sessionToken\":\"dev-looks-minor\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
			.andExpect(jsonPath("$.token.verified").value(false)));
		String reviewToken = JsonPath.read(response, "$.token.token");
		postAs(reviewToken, "/signals", "{\"momentId\":\"x\",\"reaction\":\"SAME_HERE\"}")
			.andExpect(status().isForbidden());
		// A second attempt cannot bypass review.
		postAs(reviewToken, "/verification/liveness", "{\"sessionToken\":\"dev-pass\"}")
			.andExpect(jsonPath("$.status").value("MANUAL_REVIEW"));
	}

	@Test
	void underEighteensAreRefusedAndNothingIsStored() throws Exception {
		String body = """
				{"email":"teen@example.com","password":"correct-horse-battery","dateOfBirth":"%s",
				 "displayName":"Teen","consentVersion":"2026-09"}""".formatted(
				java.time.LocalDate.now(clock).minusYears(17).toString());
		mvc.perform(MockMvcRequestBuilders.post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("UNDER_AGE"));
		mvc.perform(MockMvcRequestBuilders.post("/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"teen@example.com\",\"password\":\"correct-horse-battery\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void signalsAgeOutQuietlyAfterTheReactionWindow() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);
		String momentId = postPublicMoment(asha, "coffee");
		String signalId = sendSignal(ravi, momentId, null);

		// The story itself expires at 24 h, but the signal stays actionable for the 48 h window.
		clock.advance(Duration.ofHours(30));
		getAs(ravi, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(0)));
		getAs(asha, "/signals/digest").andExpect(jsonPath("$.signals", hasSize(1)));

		clock.advance(Duration.ofHours(19));
		getAs(asha, "/signals/digest").andExpect(jsonPath("$.signals", hasSize(0)))
			.andExpect(jsonPath("$.caughtUp").value(true));
		postAs(asha, "/signals/" + signalId + "/reveal", null).andExpect(status().isNotFound());
		getAs(ravi, "/signals/sent").andExpect(jsonPath("$[0].becameConnection").value(false));
	}

	@Test
	void theSignalBudgetMakesTheSenderPayInEffort() throws Exception {
		String sender = verifiedUser("Sam");
		locate(sender, BLR_LAT, BLR_LON);
		String[] moments = new String[4];
		for (int i = 0; i < moments.length; i++) {
			String poster = verifiedUser("Poster " + i);
			locate(poster, BLR_LAT, BLR_LON);
			moments[i] = postPublicMoment(poster, "coffee");
		}
		for (int i = 0; i < 3; i++) {
			sendSignal(sender, moments[i], null);
		}
		postAs(sender, "/signals", "{\"momentId\":\"" + moments[3] + "\",\"reaction\":\"SAME_HERE\"}")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("SIGNAL_BUDGET_EXHAUSTED"));

		clock.advance(Duration.ofHours(25));
		String late = verifiedUser("Late Poster");
		locate(late, BLR_LAT, BLR_LON);
		sendSignal(sender, postPublicMoment(late, "coffee"), null);
	}

	@Test
	void signalsCannotSmuggleArbitraryText() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		String momentId = postPublicMoment(asha, "trek");
		postAs(ravi, "/signals",
				"{\"momentId\":\"" + momentId + "\",\"reaction\":\"SAME_HERE\",\"activityRef\":\"call me now\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_REF"));
	}

	@Test
	void passingIsSilentAndSoftExitIsSilent() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);
		String signalId = sendSignal(ravi, postPublicMoment(asha, "trek"), null);
		postAs(asha, "/signals/" + signalId + "/pass", null).andExpect(status().isNoContent());
		getAs(asha, "/signals/digest").andExpect(jsonPath("$.signals", hasSize(0)));
		String trail = body(getAs(ravi, "/signals/sent"));
		assertThat(trail).doesNotContain("PASSED");

		// No "reminder" approach through her next story within the window; same answer as if still pending.
		String nextStory = postPublicMoment(asha, "coffee");
		getAs(ravi, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(0)));
		postAs(ravi, "/signals", "{\"momentId\":\"" + nextStory + "\",\"reaction\":\"SAME_HERE\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_SIGNALLED"));

		clock.advance(Duration.ofHours(49));
		String secondSignal = sendSignal(ravi, postPublicMoment(asha, "coffee"), null);
		String connectionId = JsonPath.read(body(postAs(asha, "/signals/" + secondSignal + "/reveal", null)),
				"$.connectionId");
		postAs(ravi, "/connections/" + connectionId + "/exit", null).andExpect(status().isNoContent());
		getAs(asha, "/connections").andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void sparkingNeedsYourOwnDatingLens() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		String signalId = sendSignal(ravi, postPublicMoment(asha, "trek"), null);
		String connectionId = JsonPath.read(body(postAs(asha, "/signals/" + signalId + "/reveal", null)),
				"$.connectionId");
		postAs(ravi, "/connections/" + connectionId + "/spark", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DATING_LENS_OFF"));
		getAs(ravi, "/connections").andExpect(jsonPath("$[0].conversationId").value(notNullValue()));
	}
}
