package oneday;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Multi-city: features open per city as density allows, with Right Now off globally. */
@SpringBootTest
@TestPropertySource(properties = "oneday.right-now.enabled=false")
class CityGatesIntegrationTest extends ApiTestSupport {

	private static final String BENGALURU = """
			{"name":"Bengaluru","countryCode":"in","lat":12.97,"lon":77.59,"radiusKm":30,"stage":"PILOT","features":[%s]}""";

	@Test
	void rightNowOpensInACityWhenItsGateIsSwitchedOn() throws Exception {
		String admin = promote(verifiedUser("Admin"), "ADMIN");
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		getAs(asha, "/cities/current").andExpect(status().isNoContent()); // not sharing location
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, 19.0760, 72.8777); // Mumbai: not launched
		getAs(asha, "/cities/current").andExpect(status().isNoContent());

		perform(put("/staff/cities/bengaluru"), moderator, BENGALURU.formatted("")).andExpect(status().isForbidden());
		perform(put("/staff/cities/bengaluru"), admin, BENGALURU.formatted("")).andExpect(status().isOk())
			.andExpect(jsonPath("$.countryCode").value("IN"));
		getAs(asha, "/cities/current").andExpect(jsonPath("$.name").value("Bengaluru"))
			.andExpect(jsonPath("$.stage").value("PILOT"));
		postAs(asha, "/right-now", "{\"activity\":\"badminton\",\"minutes\":60}").andExpect(status().isNotFound());

		perform(put("/staff/cities/bengaluru"), admin, BENGALURU.formatted("\"RIGHT_NOW\",\"PLANS\""))
			.andExpect(jsonPath("$.features", containsInAnyOrder("RIGHT_NOW", "PLANS")));
		postAs(asha, "/right-now", "{\"activity\":\"badminton\",\"minutes\":60}").andExpect(status().isCreated());
		postAs(ravi, "/right-now", "{\"activity\":\"badminton\",\"minutes\":60}").andExpect(status().isNotFound());

		getAs(moderator, "/staff/cities/bengaluru/density").andExpect(jsonPath("$.sharingNow").value(1));
		perform(put("/staff/cities/bengaluru"), admin, BENGALURU.replace("PILOT", "PAUSED").formatted("\"RIGHT_NOW\""))
			.andExpect(jsonPath("$.features.length()").value(0));
		getAs(asha, "/right-now/mine").andExpect(status().isNotFound());
		perform(put("/staff/cities/x"), admin, BENGALURU.formatted("").replace("30", "500")).andExpect(status().isBadRequest());
	}
}
