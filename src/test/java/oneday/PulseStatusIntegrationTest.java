package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import oneday.pulsestatus.PulseStatusService;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.ResultActions;

/** Pulse Status: friends-only, 24 hours, Spotify via the official embed, reportable where it's seen. */
@SpringBootTest
class PulseStatusIntegrationTest extends ApiTestSupport {

	private static final String TRACK = "4cOdK2wGLETKBW3PvgPWqT";

	@Autowired
	PulseStatusService statuses;

	@Test
	void onlyFriendsSeeAStatusAndItLastsADay() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String meera = verifiedUser("Meera");
		String connection = connect(asha, ravi);

		setStatus(asha, """
				{"mood":"CHILL","emoji":"🎧","note":"  Sunday   playlist ",
				 "spotifyUrl":"https://open.spotify.com/intl-hi/track/%s?si=abc","musicTitle":"Kesariya"}
				""".formatted(TRACK)).andExpect(status().isOk())
			.andExpect(jsonPath("$.moodLabel").value("Chill"))
			.andExpect(jsonPath("$.note").value("Sunday playlist"))
			.andExpect(jsonPath("$.music.embedUrl").value("https://open.spotify.com/embed/track/" + TRACK));

		getAs(ravi, "/pulse-status/friends").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].connectionId").value(connection))
			.andExpect(jsonPath("$[0].firstName").value("Asha"))
			.andExpect(jsonPath("$[0].emoji").value("🎧"))
			.andExpect(jsonPath("$[0].music.title").value("Kesariya"))
			.andExpect(jsonPath("$[0].checkIn").value(false));
		getAs(meera, "/pulse-status/friends").andExpect(jsonPath("$", hasSize(0)));

		// Updating replaces it; a low mood invites a check-in rather than a like.
		setStatus(asha, "{\"mood\":\"LOW\",\"emoji\":\"👩‍💻\"}").andExpect(status().isOk());
		getAs(ravi, "/pulse-status/friends").andExpect(jsonPath("$[0].checkIn").value(true))
			.andExpect(jsonPath("$[0].music").doesNotExist());

		clock.advance(Duration.ofHours(24).plusSeconds(1));
		getAs(ravi, "/pulse-status/friends").andExpect(jsonPath("$", hasSize(0)));
		getAs(asha, "/pulse-status/mine").andExpect(status().isNoContent());
		assertThat(statuses.purgeExpired()).isEqualTo(1);

		setStatus(asha, "{\"mood\":\"HAPPY\",\"emoji\":\"🇮🇳\"}").andExpect(status().isOk());
		getAs(asha, "/pulse-status/mine").andExpect(jsonPath("$.mood").value("HAPPY"));
		deleteAs(asha, "/pulse-status").andExpect(status().isNoContent());
		getAs(ravi, "/pulse-status/friends").andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void inputIsValidated() throws Exception {
		String asha = verifiedUser("Asha");
		setStatus(asha, "{\"mood\":\"HAPPY\",\"emoji\":\"hello\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_EMOJI"));
		setStatus(asha, "{\"mood\":\"HAPPY\",\"emoji\":\"😀😀😀😀\"}").andExpect(status().isBadRequest());
		setStatus(asha, "{\"emoji\":\"😀\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MOOD_REQUIRED"));
		setStatus(asha, "{\"mood\":\"HAPPY\",\"emoji\":\"😀\",\"spotifyUrl\":\"https://evil.example/track/x\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_MUSIC"));
		setStatus(asha, "{\"mood\":\"HAPPY\",\"emoji\":\"😀\",\"note\":\"" + "a".repeat(61) + "\"}")
			.andExpect(status().isBadRequest());
		setStatus(asha, "{\"mood\":\"HAPPY\",\"emoji\":\"👨‍👩‍👧‍👦\",\"spotifyUrl\":\"spotify:track:" + TRACK + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.music.trackId").value(TRACK));
	}

	@Test
	void aStatusCanBeBlockedFromWhereItIsSeenAndStrangersCannotNameIt() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String meera = verifiedUser("Meera");
		connect(asha, ravi);
		setStatus(asha, "{\"mood\":\"SOCIAL\",\"emoji\":\"🎉\",\"note\":\"party at mine\"}").andExpect(status().isOk());
		String statusId = JsonPath.read(body(getAs(ravi, "/pulse-status/friends")), "$[0].statusId");

		postAs(meera, "/safety/reports", "{\"pulseStatusId\":\"" + statusId + "\",\"category\":\"SPAM\"}")
			.andExpect(status().isNotFound());
		postAs(ravi, "/safety/reports",
				"{\"pulseStatusId\":\"" + statusId + "\",\"category\":\"HARASSMENT\",\"alsoBlock\":true}")
			.andExpect(status().isCreated());
		getAs(ravi, "/pulse-status/friends").andExpect(jsonPath("$", hasSize(0)));
		getAs(asha, "/privacy/export").andExpect(jsonPath("$.pulseStatus.emoji").value("🎉"));
	}

	private ResultActions setStatus(String token, String json) throws Exception {
		return perform(put("/pulse-status"), token, json);
	}
}
