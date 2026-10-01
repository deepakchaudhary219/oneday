package oneday;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import oneday.calls.CallService;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.ResultActions;

/** Layered Video: chat first, voice first, and video only as far as both people are ready to go. */
@SpringBootTest
class LayeredCallsIntegrationTest extends ApiTestSupport {

	@Autowired
	CallService calls;

	@Test
	void aCallStepsUpOnlyWhenBothAreReadyAndDownWhenEitherWants() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connection = connect(asha, ravi);
		postAs(asha, "/connections/" + connection + "/calls", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHAT_FIRST"));
		chatBothWays(asha, ravi);

		String call = JsonPath.read(body(postAs(asha, "/connections/" + connection + "/calls", null)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("RINGING"))
			.andExpect(jsonPath("$.direction").value("OUTGOING"))
			.andExpect(jsonPath("$.firstName").value("Ravi"))
			.andExpect(jsonPath("$.layer").value("VOICE"))), "$.id");
		getAs(ravi, "/calls/" + call).andExpect(jsonPath("$.direction").value("INCOMING"))
			.andExpect(jsonPath("$.firstName").value("Asha"));
		postAs(asha, "/connections/" + connection + "/calls", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BUSY"));
		layer(asha, call, "BLURRED").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_ACTIVE"));
		postAs(asha, "/calls/" + call + "/accept", null).andExpect(status().isConflict());

		postAs(ravi, "/calls/" + call + "/accept", null).andExpect(jsonPath("$.status").value("ACTIVE"));
		layer(asha, call, "BLURRED").andExpect(jsonPath("$.layer").value("VOICE"))
			.andExpect(jsonPath("$.youWant").value("BLURRED"));
		getAs(ravi, "/calls/" + call).andExpect(jsonPath("$.theyAreReadyForMore").value(true));
		layer(ravi, call, "CLEAR").andExpect(jsonPath("$.layer").value("BLURRED"));
		getAs(asha, "/calls/" + call).andExpect(jsonPath("$.theyAreReadyForMore").value(true));
		layer(asha, call, "CLEAR").andExpect(jsonPath("$.layer").value("CLEAR"));
		layer(ravi, call, "VOICE").andExpect(jsonPath("$.layer").value("VOICE")); // one person is enough to step down
		getAs(asha, "/calls/" + call).andExpect(jsonPath("$.theyAreReadyForMore").value(false));

		postAs(asha, "/calls/" + call + "/signal", "{\"kind\":\"OFFER\",\"payload\":\"v=0 o=- 1 2 IN IP4 0.0.0.0\"}")
			.andExpect(status().isAccepted());
		postAs(asha, "/calls/" + call + "/signal", "{\"kind\":\"OFFER\",\"payload\":\"" + "x".repeat(16_385) + "\"}")
			.andExpect(status().isBadRequest());
		String meera = verifiedUser("Meera");
		getAs(meera, "/calls/" + call).andExpect(status().isNotFound());
		postAs(meera, "/calls/" + call + "/signal", "{\"kind\":\"ICE\",\"payload\":\"{}\"}").andExpect(status().isNotFound());

		postAs(ravi, "/calls/" + call + "/end", null).andExpect(jsonPath("$.status").value("ENDED"))
			.andExpect(jsonPath("$.endReason").value("HUNG_UP"));
		postAs(asha, "/calls/" + call + "/signal", "{\"kind\":\"ICE\",\"payload\":\"{}\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CALL_ENDED"));
		getAs(asha, "/privacy/export").andExpect(jsonPath("$.calls", hasSize(1)));
	}

	@Test
	void unansweredCallsAreMissedAndABlockOrReportEndsThem() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connection = connect(asha, ravi);
		chatBothWays(asha, ravi);

		String first = start(asha, connection);
		clock.advance(Duration.ofSeconds(46));
		calls.sweep();
		getAs(asha, "/calls/" + first).andExpect(jsonPath("$.endReason").value("MISSED"));

		String second = start(asha, connection);
		postAs(ravi, "/calls/" + second + "/decline", null).andExpect(jsonPath("$.endReason").value("DECLINED"));

		String third = start(asha, connection);
		postAs(ravi, "/calls/" + third + "/accept", null).andExpect(status().isOk());
		postAs(verifiedUser("Meera"), "/safety/reports", "{\"callId\":\"" + third + "\",\"category\":\"HARASSMENT\"}")
			.andExpect(status().isNotFound());
		postAs(ravi, "/safety/reports", "{\"callId\":\"" + third + "\",\"category\":\"HARASSMENT\",\"alsoBlock\":true}")
			.andExpect(status().isCreated());
		deliverEvents();
		getAs(asha, "/calls/" + third).andExpect(jsonPath("$.status").value("ENDED"))
			.andExpect(jsonPath("$.endReason").value("BLOCKED"));
		postAs(asha, "/connections/" + connection + "/calls", null).andExpect(status().isConflict());
	}

	@Test
	void iceServersComeWithoutTurnUntilConfigured() throws Exception {
		String asha = verifiedUser("Asha");
		getAs(asha, "/calls/ice-servers").andExpect(jsonPath("$.iceServers", hasSize(1)))
			.andExpect(jsonPath("$.iceServers[0].urls[0]").value("stun:stun.l.google.com:19302"))
			.andExpect(jsonPath("$.iceServers[0].username").doesNotExist());
	}

	private void chatBothWays(String asha, String ravi) throws Exception {
		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");
		postAs(asha, "/conversations/" + conversation + "/messages", "{\"body\":\"Call later?\"}")
			.andExpect(status().isCreated());
		postAs(ravi, "/conversations/" + conversation + "/messages", "{\"body\":\"Sure, voice first\"}")
			.andExpect(status().isCreated());
	}

	private String start(String token, String connection) throws Exception {
		clock.advance(Duration.ofSeconds(1));
		return JsonPath.read(body(postAs(token, "/connections/" + connection + "/calls", null)
			.andExpect(status().isCreated())), "$.id");
	}

	private ResultActions layer(String token, String call, String wants) throws Exception {
		return perform(put("/calls/" + call + "/layer"), token, "{\"wants\":\"" + wants + "\"}");
	}
}
