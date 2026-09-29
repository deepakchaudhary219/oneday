package oneday;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;

class ApiDocsTest extends ApiTestSupport {

	@Test
	void openApiDocsArePublicAndListTheCoreLoop() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths", hasKey("/discover/constellation")))
			.andExpect(jsonPath("$.paths", hasKey("/signals/{signalId}/reveal")));
	}

	@Test
	void everythingElseRequiresAToken() throws Exception {
		mvc.perform(get("/discover/constellation")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
