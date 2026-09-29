package oneday.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full-stack API tests: real security filter chain, real services, Flyway schema on H2. Each test starts
 * from empty tables; the shared clock only ever moves forward.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSupport.TestClockConfig.class)
public abstract class ApiTestSupport {

	/** Koramangala, Bengaluru. */
	protected static final double BLR_LAT = 12.9352;

	protected static final double BLR_LON = 77.6245;

	private static final List<String> TABLES = List.of("staff_actions", "staff_members", "messages", "conversations", "connections", "signals",
			"moments", "media_uploads", "user_locations", "blocks", "reports", "verification_attempts", "profiles", "users");

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		TABLES.forEach(t -> jdbc.update("delete from " + t));
	}

	// ---- accounts -------------------------------------------------------------------------------------

	protected String register(String displayName) throws Exception {
		String body = """
				{"email":"%s","password":"correct-horse-battery","dateOfBirth":"1998-04-12",
				 "displayName":"%s","consentVersion":"2026-09"}
				""".formatted(displayName.toLowerCase().replace(' ', '.') + "+" + UUID.randomUUID() + "@example.com",
				displayName);
		String response = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.token");
	}

	/** Runs the dev liveness check and returns the fresh token (with the verified scope on success). */
	protected String verify(String token, String sessionToken) throws Exception {
		String response = perform(post("/verification/liveness"), token, "{\"sessionToken\":\"" + sessionToken + "\"}")
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.token.token");
	}

	protected String verifiedUser(String displayName) throws Exception {
		return verify(register(displayName), "dev-pass");
	}

	protected static String userIdOf(String jwt) {
		String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
		return JsonPath.read(payload, "$.sub");
	}

	// ---- common actions -------------------------------------------------------------------------------

	protected void updateProfile(String token, String json) throws Exception {
		perform(patch("/profile/me"), token, json).andExpect(status().isOk());
	}

	protected void locate(String token, double lat, double lon) throws Exception {
		perform(put("/location"), token, "{\"lat\":" + lat + ",\"lon\":" + lon + "}").andExpect(status().isOk());
	}

	/** Requests an upload ticket and returns its {@code mediaRef}. */
	protected String upload(String token, String kind, String contentType) throws Exception {
		String response = perform(post("/media/uploads"), token,
				"{\"kind\":\"" + kind + "\",\"contentType\":\"" + contentType + "\",\"sizeBytes\":250000}")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.mediaRef");
	}

	protected String postPublicMoment(String token, String activity) throws Exception {
		String body = """
				{"kind":"PHOTO","caption":"Sunrise at Nandi Hills","activityTag":"%s","mediaRef":"%s",
				 "shareScope":"PUBLIC_DISCOVERY","capturedLive":true,"previewAllowed":false}
				""".formatted(activity, upload(token, "PHOTO", "image/jpeg"));
		String response = perform(post("/moments"), token, body).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	protected String sendSignal(String token, String momentId, String activityRef) throws Exception {
		String activity = activityRef == null ? "null" : "\"" + activityRef + "\"";
		String response = perform(post("/signals"), token,
				"{\"momentId\":\"" + momentId + "\",\"reaction\":\"MADE_ME_SMILE\",\"activityRef\":" + activity + "}")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	protected String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	// ---- HTTP helpers ---------------------------------------------------------------------------------

	protected ResultActions getAs(String token, String url) throws Exception {
		return mvc.perform(get(url).header("Authorization", "Bearer " + token));
	}

	protected ResultActions postAs(String token, String url, String json) throws Exception {
		return perform(post(url), token, json);
	}

	protected ResultActions deleteAs(String token, String url) throws Exception {
		return mvc.perform(delete(url).header("Authorization", "Bearer " + token));
	}

	protected ResultActions perform(MockHttpServletRequestBuilder request, String token, String json)
			throws Exception {
		request.header("Authorization", "Bearer " + token);
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mvc.perform(request);
	}

	@TestConfiguration
	static class TestClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(Instant.now());
		}
	}
}
