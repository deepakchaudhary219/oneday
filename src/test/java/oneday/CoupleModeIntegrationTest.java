package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Couple Mode (blueprint v2 §7.4, "designed to graduate"): private until both confirm, then both people
 * leave Discovery Mode while Friend Mode keeps working.
 */
class CoupleModeIntegrationTest extends ApiTestSupport {

	@Test
	void bothConfirmingPausesDiscoveryBothWays() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connectionId = connect(asha, ravi);
		String priya = verifiedUser("Priya");
		locate(priya, BLR_LAT, BLR_LON);
		String priyaMoment = postPublicMoment(priya, "coffee");

		postAs(asha, "/connections/" + connectionId + "/couple", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MUTUAL_SPARK_REQUIRED"));
		mutualSpark(asha, ravi, connectionId);

		// One side's confirmation is private, like a spark.
		postAs(asha, "/connections/" + connectionId + "/couple", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.youConfirmed").value(true))
			.andExpect(jsonPath("$.coupleMode").value(false));
		String raviView = body(getAs(ravi, "/connections").andExpect(jsonPath("$[0].coupleMode").value(false)));
		assertThat(raviView).doesNotContain("youConfirmed", "coupleA", "coupleB");
		getAs(priya, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(1)));

		postAs(ravi, "/connections/" + connectionId + "/couple", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.coupleMode").value(true))
			.andExpect(jsonPath("$.message", containsString("Discovery is paused")));
		getAs(asha, "/connections").andExpect(jsonPath("$[0].coupleMode").value(true));

		// Neither seen by strangers nor browsing them; no new approaches either way.
		getAs(priya, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(0)));
		getAs(asha, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(0)))
			.andExpect(jsonPath("$.message", containsString("Couple Mode is on")));
		String ashaMoment = jdbc.queryForObject("select id from moments where owner_id = ?", String.class, userIdOf(asha));
		postAs(priya, "/signals", "{\"momentId\":\"" + ashaMoment + "\",\"reaction\":\"SAME_HERE\"}")
			.andExpect(status().isNotFound());
		postAs(asha, "/signals", "{\"momentId\":\"" + priyaMoment + "\",\"reaction\":\"SAME_HERE\"}")
			.andExpect(status().isNotFound());

		// Friend Mode keeps working, and the ledger counts it as the app doing its job.
		deliverEvents();
		getAs(asha, "/ledger").andExpect(jsonPath("$.coupleFormed").value(1))
			.andExpect(jsonPath("$.mutualSparks").value(1));

		// Either side can step back at any time; withdrawing a spark also ends Couple Mode.
		deleteAs(ravi, "/connections/" + connectionId + "/spark").andExpect(status().isOk());
		getAs(asha, "/connections").andExpect(jsonPath("$[0].coupleMode").value(false));
		getAs(priya, "/discover/constellation").andExpect(jsonPath("$.nodes", hasSize(1)));
	}
}
