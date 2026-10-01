package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The engagement layer (docs/05-engagement-psychology.md): the k-anonymous Story Map with its lenses,
 * Today's Prompt with give-to-get, Story Relays, Connection Warmth, and country-aware safety numbers.
 */
class EngagementIntegrationTest extends ApiTestSupport {

	/** MG Road area: two different ~0.7 km² cells inside the same ~5 km area. */
	private static final double[] MG_1 = { 12.9716, 77.5946 };

	private static final double[] MG_2 = { 12.9760, 77.5946 };

	/** Same ~5 km area as Koramangala, different cell. */
	private static final double[] KORA_NORTH = { 12.9442, 77.6245 };

	@Test
	void theStoryMapNeverPinsASinglePerson() throws Exception {
		String viewer = verifiedUser("Viewer");
		updateProfile(viewer, "{\"discoveryRadiusKm\":15,\"homeRegion\":\"IN-KL\"}");
		locate(viewer, BLR_LAT, BLR_LON);
		// Three people in one Koramangala cell (one of them a private account: share scope is per story).
		String asha = poster("Asha", BLR_LAT, BLR_LON, "{\"homeRegion\":\"IN-KL\",\"accountPrivacy\":\"PRIVATE\"}");
		String ravi = poster("Ravi", BLR_LAT, BLR_LON, "{\"homeRegion\":\"IN-KL\"}");
		String meera = poster("Meera", BLR_LAT, BLR_LON, "{\"homeRegion\":\"IN-KL\"}");
		// One person alone in a nearby cell, and one posting from inside her Safe Zone.
		String dev = poster("Dev", KORA_NORTH[0], KORA_NORTH[1], null);
		String fatima = verifiedUser("Fatima");
		locate(fatima, BLR_LAT, BLR_LON);
		perform(put("/location/safe-zone"), fatima,
				null)
			.andExpect(status().isOk());
		// Two people in different cells of the MG Road area.
		String gita = poster("Gita", MG_1[0], MG_1[1], null);
		String hari = poster("Hari", MG_2[0], MG_2[1], null);
		for (String t : List.of(asha, ravi, meera, dev, fatima, gita, hari)) {
			postText(t, "trek", null).andExpect(status().isCreated());
		}

		String map = body(getAs(viewer, "/map/stories").andExpect(status().isOk())
			.andExpect(jsonPath("$.clusters", hasSize(2)))
			.andExpect(jsonPath("$.clusters[0].id").value("tdr1w6"))
			.andExpect(jsonPath("$.clusters[0].level").value("NEIGHBOURHOOD"))
			.andExpect(jsonPath("$.clusters[0].people").value("3"))
			.andExpect(jsonPath("$.clusters[0].fromYourHomeRegion").value(3))
			.andExpect(jsonPath("$.clusters[0].vibe").value("trek"))
			.andExpect(jsonPath("$.clusters[0].liveNow").value(true))
			.andExpect(jsonPath("$.clusters[0].stories", hasSize(3)))
			.andExpect(jsonPath("$.clusters[0].stories[0].sharedHomeRegion").value("IN-KL"))
			.andExpect(jsonPath("$.clusters[1].id").value("tdr1v"))
			.andExpect(jsonPath("$.clusters[1].level").value("AREA"))
			.andExpect(jsonPath("$.clusters[1].people").value("2"))
			// The lone poster and the Safe-Zone poster are never placed.
			.andExpect(jsonPath("$.aroundYourCity.level").value("CITY"))
			.andExpect(jsonPath("$.aroundYourCity.centerLat").value(nullValue()))
			.andExpect(jsonPath("$.aroundYourCity.stories", hasSize(2))));
		for (String t : List.of(asha, ravi, dev, fatima, viewer)) {
			assertThat(map).doesNotContain(userIdOf(t));
		}
		assertThat(map).doesNotContain("relevance", "rank", "12.9352");

		// Wider lenses never go below area level: Roots shows the three Keralites as one ~5 km area.
		getAs(viewer, "/map/stories?scope=ROOTS").andExpect(jsonPath("$.clusters", hasSize(1)))
			.andExpect(jsonPath("$.clusters[0].id").value("tdr1w"))
			.andExpect(jsonPath("$.clusters[0].level").value("AREA"))
			.andExpect(jsonPath("$.aroundYourCity").value(nullValue()));
		getAs(viewer, "/map/stories?activity=coffee").andExpect(jsonPath("$.clusters", hasSize(0)))
			.andExpect(jsonPath("$.message", containsString("Quiet")));

		// A block removes the person from the map, which then re-clusters without them.
		postAs(viewer, "/safety/blocks", "{\"momentId\":\"" + firstMomentOf(meera) + "\"}")
			.andExpect(status().isNoContent());
		getAs(viewer, "/map/stories").andExpect(jsonPath("$.clusters[0].id").value("tdr1w"))
			.andExpect(jsonPath("$.clusters[0].level").value("AREA"));
	}

	@Test
	void todaysPromptUnlocksOthersOnlyAfterYouShare() throws Exception {
		String viewer = verifiedUser("Viewer");
		updateProfile(viewer, "{\"homeRegion\":\"IN-KL\"}");
		locate(viewer, BLR_LAT, BLR_LON);
		String asha = poster("Asha", BLR_LAT, BLR_LON, "{\"homeRegion\":\"IN-KL\"}");
		String prompt = body(getAs(asha, "/prompts/today").andExpect(status().isOk()));
		String key = JsonPath.read(prompt, "$.promptKey");
		assertThat(key).startsWith("catalog:");
		postText(asha, "coffee", key).andExpect(status().isCreated())
			.andExpect(jsonPath("$.answersPrompt").value(true));

		// A truthful teaser and a curiosity gap, closed only by contributing.
		getAs(viewer, "/prompts/today").andExpect(jsonPath("$.text").value((String) JsonPath.read(prompt, "$.text")))
			.andExpect(jsonPath("$.unlocked").value(false))
			.andExpect(jsonPath("$.answeredNearby").value("1"))
			.andExpect(jsonPath("$.fromYourHomeRegion").value(1))
			.andExpect(jsonPath("$.answers", hasSize(0)))
			.andExpect(jsonPath("$.message", containsString("Share yours publicly")));
		getAs(viewer, "/pulse").andExpect(jsonPath("$.headline", containsString("answered today's prompt")))
			.andExpect(jsonPath("$.todaysPrompt").value((String) JsonPath.read(prompt, "$.text")));
		postAs(viewer, "/moments", """
				{"kind":"TEXT","caption":"mine","shareScope":"FRIENDS_ONLY","promptKey":"%s"}""".formatted(key))
			.andExpect(status().isCreated());
		getAs(viewer, "/prompts/today").andExpect(jsonPath("$.answeredByYou").value(true))
			.andExpect(jsonPath("$.unlocked").value(false));
		postText(viewer, "coffee", key).andExpect(status().isCreated());
		getAs(viewer, "/prompts/today").andExpect(jsonPath("$.unlocked").value(true))
			.andExpect(jsonPath("$.answers", hasSize(1)))
			.andExpect(jsonPath("$.answers[0].firstName").value("Asha"))
			.andExpect(jsonPath("$.answers[0].fromYourHomeRegion").value(true));
		getAs(viewer, "/map/stories?todaysPrompt=true").andExpect(jsonPath("$.aroundYourCity.stories", hasSize(1)));

		postText(viewer, "coffee", "catalog:1999-01-01").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PROMPT_NOT_TODAY"));

		// Staff can schedule a Roots prompt: people from that region see it, everyone else the daily one.
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
		postAs(moderator, "/staff/prompts", """
				{"date":"%s","homeRegion":"in-kl","text":"Show us a taste of Kerala in your city","activityHint":"food"}
				""".formatted(today)).andExpect(status().isCreated());
		getAs(viewer, "/prompts/today").andExpect(jsonPath("$.rootsPrompt").value(true))
			.andExpect(jsonPath("$.text").value("Show us a taste of Kerala in your city"))
			.andExpect(jsonPath("$.unlocked").value(false));
		getAs(moderator, "/prompts/today").andExpect(jsonPath("$.rootsPrompt").value(false));
		postAs(viewer, "/staff/prompts", "{\"date\":\"" + today + "\",\"text\":\"x\"}").andExpect(status().isForbidden());
	}

	@Test
	void storyRelaysAreAnsweredByOthersAndRespectBlocks() throws Exception {
		String asha = poster("Asha", BLR_LAT, BLR_LON, null);
		String ravi = poster("Ravi", BLR_LAT, BLR_LON, null);
		String meera = poster("Meera", BLR_LAT, BLR_LON, null);
		String root = JsonPath.read(body(postText(asha, "trek", null)), "$.id");

		postRelay(asha, root, "PUBLIC_DISCOVERY").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("CANNOT_RELAY_SELF"));
		postRelay(ravi, root, "FRIENDS_ONLY").andExpect(jsonPath("$.code").value("RELAY_MUST_BE_PUBLIC"));
		String raviLink = JsonPath.read(body(postRelay(ravi, root, "PUBLIC_DISCOVERY").andExpect(status().isCreated())
			.andExpect(jsonPath("$.relayId").value(root))
			.andExpect(jsonPath("$.relayPosition").value(1))), "$.id");
		postRelay(ravi, root, "PUBLIC_DISCOVERY").andExpect(jsonPath("$.code").value("ALREADY_IN_RELAY"));
		// Answering a link joins the same relay, one step further.
		postRelay(meera, raviLink, "PUBLIC_DISCOVERY").andExpect(jsonPath("$.relayId").value(root))
			.andExpect(jsonPath("$.relayPosition").value(2));

		getAs(meera, "/moments/" + root + "/relay").andExpect(status().isOk())
			.andExpect(jsonPath("$.length").value(3))
			.andExpect(jsonPath("$.links[0].firstName").value("Asha"))
			.andExpect(jsonPath("$.links[2].mine").value(true));
		getAs(asha, "/pulse").andExpect(jsonPath("$.relayAnswers").value("2"))
			.andExpect(jsonPath("$.headline").value("Your moment started a relay: 2 people answered"));

		// After Asha blocks Meera, neither sees the other in the relay; Asha can't reach Meera's link at all.
		postAs(asha, "/safety/blocks", "{\"momentId\":\"" + firstMomentOf(meera) + "\"}").andExpect(status().isNoContent());
		getAs(asha, "/moments/" + root + "/relay").andExpect(jsonPath("$.length").value(2));
		getAs(meera, "/moments/" + root + "/relay").andExpect(status().isNotFound());
	}

	@Test
	void warmthGrowsWithMutualRhythmAndQuietComesWithAStarter() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		updateProfile(asha, "{\"activities\":[\"trek\",\"chess\"]}");
		updateProfile(ravi, "{\"activities\":[\"chess\"]}");
		String connectionId = connect(asha, ravi);
		getAs(asha, "/connections").andExpect(jsonPath("$[0].warmth.level").value("NEW"))
			.andExpect(jsonPath("$[0].warmth.starter", containsString("chess")));

		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");
		postAs(asha, "/conversations/" + conversation + "/messages", "{\"body\":\"hi!\"}").andExpect(status().isCreated());
		postAs(ravi, "/conversations/" + conversation + "/messages", "{\"body\":\"hey\"}").andExpect(status().isCreated());
		getAs(ravi, "/connections").andExpect(jsonPath("$[0].warmth.level").value("KINDLING"))
			.andExpect(jsonPath("$[0].warmth.starter").value(nullValue()));

		// Going quiet is never punished: no countdown, no loss, just a gentle starter.
		clock.advance(Duration.ofDays(15));
		String quiet = body(getAs(asha, "/connections").andExpect(jsonPath("$[0].warmth.level").value("QUIET"))
			.andExpect(jsonPath("$[0].warmth.line", containsString("that's okay")))
			.andExpect(jsonPath("$[0].warmth.starter", containsString("chess"))));
		assertThat(quiet).doesNotContainIgnoringCase("lose").doesNotContainIgnoringCase("streak");
		assertThat(connectionId).isNotBlank();
	}

	@Test
	void safetyNumbersFollowTheCountry() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		getAs(asha, "/profile/me").andExpect(jsonPath("$.country").value("IN"))
			.andExpect(jsonPath("$.emergencyNumber").value("112"));
		updateProfile(asha, "{\"country\":\"us\",\"timeZone\":\"America/New_York\"}");
		getAs(asha, "/profile/me").andExpect(jsonPath("$.emergencyNumber").value("911"));
		perform(patch("/profile/me"), asha,
				"{\"country\":\"XX\"}")
			.andExpect(status().isBadRequest());
		String connectionId = connect(asha, ravi);
		Instant start = clock.instant().plus(Duration.ofHours(1));
		String plan = body(postAs(asha, "/dates", """
				{"connectionId":"%s","placeName":"Central Park, Bethesda Terrace","startsAt":"%s","endsAt":"%s"}
				""".formatted(connectionId, start, start.plus(Duration.ofHours(2)))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.emergencyNumber").value("911")));
		getAs(ravi, "/dates/" + JsonPath.read(plan, "$.id")).andExpect(jsonPath("$.emergencyNumber").value("112"));
	}

	private String poster(String name, double lat, double lon, String profileJson) throws Exception {
		String token = verifiedUser(name);
		if (profileJson != null) {
			updateProfile(token, profileJson);
		}
		locate(token, lat, lon);
		return token;
	}

	private ResultActions postText(String token, String activity, String promptKey) throws Exception {
		String prompt = promptKey == null ? "" : ",\"promptKey\":\"" + promptKey + "\"";
		return postAs(token, "/moments", """
				{"kind":"TEXT","caption":"Out and about","activityTag":"%s","shareScope":"PUBLIC_DISCOVERY"%s}"""
			.formatted(activity, prompt));
	}

	private ResultActions postRelay(String token, String replyTo, String scope) throws Exception {
		return postAs(token, "/moments", """
				{"kind":"TEXT","caption":"Same here","activityTag":"trek","shareScope":"%s","replyToMomentId":"%s"}"""
			.formatted(scope, replyTo));
	}

	private String firstMomentOf(String token) {
		return jdbc.queryForObject("select id from moments where owner_id = ? order by id limit 1", String.class,
				userIdOf(token));
	}
}
