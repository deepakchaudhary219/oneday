package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** The "Roots & Radius" map, location privacy defences, bounded discovery, and DPDP data rights. */
class MapAndPrivacyIntegrationTest extends ApiTestSupport {

	/** Whitefield, ~14 km east of Koramangala. */
	private static final double WHITEFIELD_LAT = 12.9698;

	private static final double WHITEFIELD_LON = 77.7500;

	@Test
	void rootsAndLanguageScopesAreCityWideButOnlyCityPrecise() throws Exception {
		String viewer = verifiedUser("Anjali");
		updateProfile(viewer, "{\"homeRegion\":\"IN-KL\",\"languages\":[\"ml\"]}");
		locate(viewer, BLR_LAT, BLR_LON);

		String fromHome = verifiedUser("Joseph");
		updateProfile(fromHome, "{\"homeRegion\":\"IN-KL\",\"languages\":[\"ml\"]}");
		locate(fromHome, WHITEFIELD_LAT, WHITEFIELD_LON);
		String fromHomeMoment = postPublicMoment(fromHome, "onam sadhya");

		String neighbour = verifiedUser("Karthik");
		updateProfile(neighbour, "{\"homeRegion\":\"IN-TN\",\"languages\":[\"ta\"]}");
		locate(neighbour, BLR_LAT, BLR_LON);
		String neighbourMoment = postPublicMoment(neighbour, "cricket");

		getAs(viewer, "/discover/constellation?scope=ROOTS").andExpect(jsonPath("$.nodes", hasSize(1)))
			.andExpect(jsonPath("$.nodes[0].nodeId").value(fromHomeMoment))
			.andExpect(jsonPath("$.nodes[0].band").value("IN_CITY"))
			.andExpect(jsonPath("$.nodes[0].direction").value("NONE"))
			.andExpect(jsonPath("$.nodes[0].sharedHomeRegion").value("IN-KL"));

		getAs(viewer, "/discover/constellation?scope=LANGUAGE").andExpect(jsonPath("$.nodes", hasSize(1)))
			.andExpect(jsonPath("$.nodes[0].nodeId").value(fromHomeMoment));

		// The 5 km default radius only reaches the neighbour, at band precision.
		getAs(viewer, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(1)))
			.andExpect(jsonPath("$.nodes[0].nodeId").value(neighbourMoment))
			.andExpect(jsonPath("$.nodes[0].band").value("UNDER_1_KM"))
			.andExpect(jsonPath("$.nodes[0].sharedHomeRegion").value(org.hamcrest.Matchers.nullValue()));

		getAs(viewer, "/discover/constellation?activity=Cricket").andExpect(jsonPath("$.nodes", hasSize(1)));
		getAs(viewer, "/discover/constellation?activity=chess").andExpect(jsonPath("$.nodes", hasSize(0)));

		String noRoots = verifiedUser("Nobody");
		locate(noRoots, BLR_LAT, BLR_LON);
		getAs(noRoots, "/discover/constellation?scope=ROOTS").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOME_REGION_REQUIRED"));
	}

	@Test
	void safeZonesCollapsePrecisionToNearbyArea() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);
		postPublicMoment(asha, "coffee");
		perform(put("/location/safe-zone"), asha, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.insideSafeZone").value(true));

		getAs(ravi, "/discover/constellation").andExpect(jsonPath("$.nodes[0].band").value("NEARBY_AREA"))
			.andExpect(jsonPath("$.nodes[0].distance").value("nearby area"))
			.andExpect(jsonPath("$.nodes[0].direction").value("NONE"));
	}

	@Test
	void pausingLocationRemovesYouFromDiscoveryImmediately() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);
		String momentId = postPublicMoment(asha, "coffee");
		getAs(ravi, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(1)));

		postAs(asha, "/location/pause", null).andExpect(status().isNoContent());
		getAs(ravi, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(0)));
		getAs(ravi, "/moments/" + momentId).andExpect(status().isNotFound());
		getAs(asha, "/location").andExpect(status().isNotFound());
		getAs(asha, "/moments/" + momentId).andExpect(jsonPath("$.layer").value("FULL"));
	}

	@Test
	void locationIngestResistsSpoofingAndProbing() throws Exception {
		String token = register("Prober");
		locate(token, BLR_LAT, BLR_LON);
		String stored = body(getAs(token, "/location").andExpect(status().isOk()));
		assertThat((String) JsonPath.read(stored, "$.area")).hasSize(6);
		assertThat(stored).doesNotContain("12.9352", "77.6245", "\"lat\"");

		// Rapid-fire updates are refused.
		perform(put("/location"), token, "{\"lat\":12.95,\"lon\":77.66}").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOCATION_UPDATE_TOO_FREQUENT"));

		// Teleporting Bengaluru → Delhi in a minute is refused.
		clock.advance(Duration.ofSeconds(60));
		perform(put("/location"), token, "{\"lat\":28.6139,\"lon\":77.2090}").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("LOCATION_IMPLAUSIBLE"));

		// Walking cell to cell is allowed, but only a limited number of distinct cells per hour (probe budget).
		int accepted = 1;
		int status = 200;
		for (int hop = 1; hop <= 20 && status == 200; hop++) {
			clock.advance(Duration.ofSeconds(31));
			status = perform(put("/location"), token,
					"{\"lat\":" + BLR_LAT + ",\"lon\":" + (BLR_LON + hop * 0.0125) + "}")
				.andReturn()
				.getResponse()
				.getStatus();
			if (status == 200) {
				accepted++;
			}
		}
		assertThat(status).isEqualTo(429);
		assertThat(accepted).isEqualTo(12);
	}

	@Test
	void publicSharesMustBeLiveCapturesWithLocation() throws Exception {
		String token = verifiedUser("Tara");
		String galleryUpload = """
				{"kind":"PHOTO","mediaRef":"media/old.jpg","shareScope":"PUBLIC_DISCOVERY","capturedLive":false}""";
		postAs(token, "/moments", galleryUpload).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("LIVE_CAPTURE_REQUIRED"));
		String live = """
				{"kind":"PHOTO","mediaRef":"media/now.jpg","shareScope":"PUBLIC_DISCOVERY","capturedLive":true}""";
		postAs(token, "/moments", live).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
		String friendsOnly = """
				{"kind":"PHOTO","mediaRef":"media/old.jpg","shareScope":"FRIENDS_ONLY","capturedLive":false}""";
		postAs(token, "/moments", friendsOnly).andExpect(status().isCreated());
	}

	@Test
	void discoveryIsBoundedAndEndsInCaughtUp() throws Exception {
		String viewer = verifiedUser("Viewer");
		locate(viewer, BLR_LAT, BLR_LON);
		for (int i = 0; i < 13; i++) {
			String poster = verifiedUser("Poster" + i);
			locate(poster, BLR_LAT, BLR_LON);
			postPublicMoment(poster, "coffee");
		}
		getAs(viewer, "/discover/constellation?page=0").andExpect(jsonPath("$.nodes", hasSize(12)))
			.andExpect(jsonPath("$.caughtUp").value(false));
		getAs(viewer, "/discover/constellation?page=1").andExpect(jsonPath("$.nodes", hasSize(1)))
			.andExpect(jsonPath("$.caughtUp").value(true))
			.andExpect(jsonPath("$.message").value("You're caught up for now. Go do something real ✨"));
		getAs(viewer, "/discover/constellation?page=3").andExpect(jsonPath("$.nodes", hasSize(0)))
			.andExpect(jsonPath("$.caughtUp").value(true));
	}

	@Test
	void heatLayerOnlyShowsAreasWithAtLeastKPeople() throws Exception {
		String viewer = verifiedUser("Viewer");
		locate(viewer, BLR_LAT, BLR_LON);
		String first = verifiedUser("First");
		locate(first, BLR_LAT, BLR_LON);
		postPublicMoment(first, "coffee");
		postPublicMoment(first, "coffee");
		getAs(viewer, "/discover/heat").andExpect(jsonPath("$", hasSize(0)));

		String second = verifiedUser("Second");
		locate(second, BLR_LAT, BLR_LON);
		postPublicMoment(second, "coffee");
		getAs(viewer, "/discover/heat").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].activity").value("coffee"))
			.andExpect(jsonPath("$[0].level").value("LOW"))
			.andExpect(jsonPath("$[0].area").value(org.hamcrest.Matchers.hasLength(5)));
	}

	@Test
	void discretionModeKeepsThePulseNeutral() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		sendSignal(ravi, postPublicMoment(asha, "coffee"), null);
		updateProfile(asha, "{\"discretionMode\":true}");
		getAs(asha, "/pulse").andExpect(jsonPath("$.headline").value("You have an update"))
			.andExpect(jsonPath("$.pendingSignals").value("1"));
	}

	@Test
	void usersCanExportAndEraseEverything() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		String signalId = sendSignal(ravi, postPublicMoment(asha, "trek"), null);
		String conversationId = JsonPath.read(body(postAs(asha, "/signals/" + signalId + "/reveal", null)),
				"$.conversationId");
		postAs(asha, "/conversations/" + conversationId + "/messages", "{\"body\":\"hey!\"}")
			.andExpect(status().isCreated());

		String export = body(getAs(asha, "/privacy/export").andExpect(status().isOk())
			.andExpect(jsonPath("$.moments", hasSize(1)))
			.andExpect(jsonPath("$.signalsReceived", hasSize(1)))
			.andExpect(jsonPath("$.messagesSent[0].body").value("hey!"))
			.andExpect(jsonPath("$.account.consentVersion").value("2026-09")));
		assertThat(export).doesNotContain(userIdOf(ravi));
		String email = JsonPath.read(export, "$.account.email");

		deleteAs(asha, "/privacy/account").andExpect(status().isBadRequest());
		deleteAs(asha, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());

		mvc.perform(MockMvcRequestBuilders.post("/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}"))
			.andExpect(status().isUnauthorized());
		getAs(ravi, "/connections").andExpect(jsonPath("$", hasSize(0)));
		getAs(ravi, "/conversations/" + conversationId + "/messages").andExpect(status().isNotFound());
	}

	@Test
	void profileRejectsOverSharingAndBadInput() throws Exception {
		String token = register("Dev");
		perform(MockMvcRequestBuilders.patch("/profile/me"), token, "{\"homeRegion\":\"Kochi\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REGION"));
		perform(MockMvcRequestBuilders.patch("/profile/me"), token, "{\"discoveryRadiusKm\":500}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RADIUS"));
		perform(MockMvcRequestBuilders.patch("/profile/me"), token, "{\"values\":[\"CASTE\"]}")
			.andExpect(status().isBadRequest());
	}
}
