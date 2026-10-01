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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import oneday.events.OutboxRelay;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full-stack API tests: real security filter chain, real services, Flyway schema on H2
 * (or MySQL, when {@code SPRING_DATASOURCE_URL} points at one). Each test starts
 * from empty tables; the shared clock only ever moves forward.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class ApiTestSupport {

	/** Koramangala, Bengaluru. */
	protected static final double BLR_LAT = 12.9352;

	protected static final double BLR_LON = 77.6245;

	private static final List<String> TABLES = List.of("calls", "e2ee_envelopes", "e2ee_one_time_prekeys", "e2ee_devices", "app_attest_keys", "attestation_challenges", "pulse_statuses", "consent_records", "payment_webhook_events", "subscriptions", "vouches", "plan_messages", "plan_members", "plans", "idempotency_keys", "right_now_joins", "right_now_sessions", "festival_seasons", "weekly_actives", "wellbeing_answers", "daily_prompts", "processed_events", "outbox_events", "ledger_entries", "date_participants", "date_plans",
			"meeting_points", "grievances", "sessions", "notices", "pulse_deliveries", "devices", "staff_actions", "staff_members", "messages", "conversations", "connections", "signals",
			"moments", "media_uploads", "user_locations", "blocks", "reports", "verification_attempts", "otp_challenges", "profiles", "users");

	protected static final String PASSWORD = "correct-horse-battery";

	/** Emails of the accounts registered by this test, by user id, so they can sign in again. */
	private final Map<String, String> emails = new HashMap<>();

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected OutboxRelay relay;

	@BeforeEach
	void cleanDatabase() {
		TABLES.forEach(t -> jdbc.update("delete from " + t));
	}

	// ---- accounts -------------------------------------------------------------------------------------

	protected String register(String displayName) throws Exception {
		String email = displayName.toLowerCase().replace(' ', '.') + "+" + UUID.randomUUID() + "@example.com";
		String body = """
				{"email":"%s","password":"%s","dateOfBirth":"1998-04-12",
				 "displayName":"%s","consentVersion":"2026-09"}
				""".formatted(email, PASSWORD, displayName);
		String response = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(response, "$.token");
		emails.put(userIdOf(token), email);
		return token;
	}

	/** Signs the user in again on another device (a new session) and returns its access token. */
	protected String signIn(String token) throws Exception {
		return JsonPath.read(body(login(emailOf(token)).andExpect(status().isOk())), "$.token");
	}

	protected ResultActions login(String email) throws Exception {
		return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
	}

	protected String emailOf(String token) {
		return emails.get(userIdOf(token));
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

	/** Inserts a staff row and returns a fresh token carrying the staff scopes. */
	protected String promote(String token, String role) throws Exception {
		jdbc.update("insert into staff_members (user_id, role, granted_at) values (?, ?, ?)", userIdOf(token), role,
				java.sql.Timestamp.from(Instant.now()));
		return verify(token, "dev-pass");
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

	/** Where the dev object store's signed URLs point (see {@code oneday.media.dev-base-url}). */
	protected static final String DEV_MEDIA = "http://localhost:8080/dev-media/";

	/**
	 * The real client flow with a genuine file: ticket → signed PUT → complete. Processing runs inline in
	 * tests, so the returned {@code mediaRef} is READY (or REJECTED for bad files).
	 */
	protected String upload(String token, String kind, String contentType) throws Exception {
		byte[] bytes = switch (kind) {
			case "VIDEO" -> TestMedia.mp4WithMetadata(2);
			default -> "image/png".equals(contentType) ? TestMedia.png(64, 48) : TestMedia.jpeg(64, 48);
		};
		return uploadBytes(token, kind, contentType, bytes, true);
	}

	protected String uploadBytes(String token, String kind, String contentType, byte[] bytes, boolean complete)
			throws Exception {
		String ticket = body(perform(post("/media/uploads"), token, "{\"kind\":\"" + kind + "\",\"contentType\":\""
				+ contentType + "\",\"sizeBytes\":" + bytes.length + "}")
			.andExpect(status().isCreated()));
		putToDevStorage(JsonPath.read(ticket, "$.uploadUrl"), contentType, bytes).andExpect(status().isOk());
		String mediaRef = JsonPath.read(ticket, "$.mediaRef");
		if (complete) {
			perform(post("/media/uploads/complete"), token, "{\"mediaRef\":\"" + mediaRef + "\"}")
				.andExpect(status().isOk());
		}
		return mediaRef;
	}

	protected ResultActions putToDevStorage(String signedUrl, String contentType, byte[] bytes) throws Exception {
		return mvc.perform(put(signedUrl.substring("http://localhost:8080".length())).contentType(contentType)
			.content(bytes));
	}

	protected byte[] fetchFromDevStorage(String signedUrl) throws Exception {
		return mvc.perform(get(signedUrl.substring("http://localhost:8080".length())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
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

	/**
	 * The core loop in one call: {@code first} posts, {@code second} signals, {@code first} reveals. Both must
	 * be verified. Returns the new connection id.
	 */
	protected String connect(String first, String second) throws Exception {
		locate(first, BLR_LAT, BLR_LON);
		locate(second, BLR_LAT, BLR_LON);
		String signalId = sendSignal(second, postPublicMoment(first, "trek"), "trek");
		return JsonPath.read(body(postAs(first, "/signals/" + signalId + "/reveal", null).andExpect(status().isOk())),
				"$.connectionId");
	}

	/** Both people turn on the Dating Lens and spark: a Mutual Spark. */
	protected void mutualSpark(String first, String second, String connectionId) throws Exception {
		updateProfile(first, "{\"datingLens\":true}");
		updateProfile(second, "{\"datingLens\":true}");
		postAs(first, "/connections/" + connectionId + "/spark", null).andExpect(status().isOk());
		postAs(second, "/connections/" + connectionId + "/spark", null).andExpect(status().isOk());
	}

	/** Delivers every committed domain event to its consumers, as the background relay would. */
	protected int deliverEvents() {
		return relay.drain();
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
}
