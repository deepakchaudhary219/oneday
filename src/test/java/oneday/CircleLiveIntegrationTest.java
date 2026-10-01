package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import oneday.live.LiveService;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Circle Live: only your circle can watch, tokens carry the right grants, and lives end on time. */
@SpringBootTest
class CircleLiveIntegrationTest extends ApiTestSupport {

	@Autowired
	LiveService live;

	@Test
	void connectionsWatchWithSubscribeOnlyTokensAndStrangersCannot() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String stranger = verifiedUser("Kabir");
		connect(asha, ravi);

		String started = body(postAs(asha, "/live", "{\"title\":\"Sunset at Nandi\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.live.state").value("LIVE"))
			.andExpect(jsonPath("$.access.serverUrl").value("ws://localhost:7880")));
		String liveId = JsonPath.read(started, "$.live.id");
		Map<String, Object> hostGrant = grant(JsonPath.read(started, "$.access.token"));
		assertThat(hostGrant.get("canPublish")).isEqualTo(true);
		postAs(asha, "/live", null).andExpect(status().isConflict());

		getAs(ravi, "/live").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].hostFirstName").value("Asha"));
		getAs(stranger, "/live").andExpect(jsonPath("$", hasSize(0)));
		postAs(stranger, "/live/" + liveId + "/join", null).andExpect(status().isNotFound());

		String joined = body(postAs(ravi, "/live/" + liveId + "/join", null).andExpect(status().isOk()));
		String viewerToken = JsonPath.read(joined, "$.access.token");
		Map<String, Object> viewerGrant = grant(viewerToken);
		assertThat(viewerGrant.get("canPublish")).isEqualTo(false);
		assertThat(viewerGrant.get("room")).isEqualTo(liveId);
		assertThat(new String(Base64.getUrlDecoder().decode(viewerToken.split("\\.")[1]), StandardCharsets.UTF_8))
			.doesNotContain(userIdOf(ravi));
		getAs(asha, "/live/" + liveId + "/host").andExpect(jsonPath("$.viewers").value(1));
		getAs(ravi, "/live/" + liveId + "/host").andExpect(status().isNotFound());

		postAs(stranger, "/safety/reports", "{\"liveId\":\"" + liveId + "\",\"category\":\"SPAM\"}")
			.andExpect(status().isNotFound());
		postAs(ravi, "/safety/reports", "{\"liveId\":\"" + liveId + "\",\"category\":\"HARASSMENT\",\"alsoBlock\":true}")
			.andExpect(status().isCreated());
		postAs(ravi, "/live/" + liveId + "/join", null).andExpect(status().isNotFound());

		clock.advance(Duration.ofMinutes(61));
		assertThat(live.sweep()).isEqualTo(1);
		asha = signIn(asha);
		getAs(asha, "/live/" + liveId + "/host").andExpect(jsonPath("$.live.state").value("ENDED"));
	}

	@Test
	void aThreadLiveIsForThatThreadOnly() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String meera = verifiedUser("Meera");
		String withRavi = connect(asha, ravi);
		connect(asha, meera);
		String thread = JsonPath.read(body(postAs(asha, "/threads",
				"{\"title\":\"Wedding\",\"inviteConnectionIds\":[\"" + withRavi + "\"]}")), "$.id");
		postAs(ravi, "/threads/" + thread + "/accept", null).andExpect(status().isOk());
		postAs(meera, "/live", "{\"threadId\":\"" + thread + "\"}").andExpect(status().isNotFound());
		String liveId = JsonPath.read(body(postAs(asha, "/live", "{\"threadId\":\"" + thread + "\"}")
			.andExpect(jsonPath("$.live.audience").value("THREAD"))), "$.live.id");
		getAs(ravi, "/live").andExpect(jsonPath("$", hasSize(1)));
		getAs(meera, "/live").andExpect(jsonPath("$", hasSize(0))); // a Connection, but not in the thread
		postAs(meera, "/live/" + liveId + "/join", null).andExpect(status().isNotFound());
		postAs(asha, "/live/" + liveId + "/end", null).andExpect(jsonPath("$.state").value("ENDED"));
		getAs(ravi, "/live").andExpect(jsonPath("$", hasSize(0)));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> grant(String jwt) {
		String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
		return (Map<String, Object>) JsonPath.read(payload, "$.video");
	}
}
