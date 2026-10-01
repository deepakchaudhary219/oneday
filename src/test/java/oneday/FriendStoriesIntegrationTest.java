package oneday;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** The story rings: your Connections' live stories, grouped per person, yours first, never strangers'. */
@SpringBootTest
class FriendStoriesIntegrationTest extends ApiTestSupport {

	@Test
	void connectionsStoriesAreGroupedWithYoursFirst() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String stranger = verifiedUser("Kabir");
		String connection = connect(asha, ravi); // Asha posted one public moment during connect
		locate(stranger, BLR_LAT, BLR_LON);
		postPublicMoment(stranger, "chess");
		clock.advance(Duration.ofMinutes(5));
		postAs(ravi, "/moments", "{\"kind\":\"TEXT\",\"caption\":\"Chai later?\",\"shareScope\":\"FRIENDS_ONLY\"}")
			.andExpect(status().isCreated());

		getAs(asha, "/moments/friends").andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].mine").value(true))
			.andExpect(jsonPath("$[1].firstName").value("Ravi"))
			.andExpect(jsonPath("$[1].moments[0].caption").value("Chai later?"))
			.andExpect(jsonPath("$[1].moments[0].layer").value("FULL"));

		postAs(asha, "/safety/blocks", "{\"connectionId\":\"" + connection + "\"}").andExpect(status().isNoContent());
		getAs(asha, "/moments/friends").andExpect(jsonPath("$", hasSize(1)));

		clock.advance(Duration.ofHours(25));
		asha = signIn(asha);
		getAs(asha, "/moments/friends").andExpect(jsonPath("$", hasSize(0))); // stories last a day
	}

	@Test
	void theChatListPreviewsTheLatestMessageAndPutsRecentChatsFirst() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String meera = verifiedUser("Meera");
		connect(asha, ravi);
		clock.advance(Duration.ofMinutes(1));
		connect(asha, meera);
		List<String> ids = JsonPath.read(body(getAs(asha, "/connections")), "$[?(@.displayName=='Ravi')].conversationId");
		String withRavi = ids.get(0);
		getAs(asha, "/connections").andExpect(jsonPath("$[0].displayName").value("Meera")) // newest connection first
			.andExpect(jsonPath("$[0].lastMessage").doesNotExist());

		clock.advance(Duration.ofMinutes(1));
		postAs(ravi, "/conversations/" + withRavi + "/messages", "{\"body\":\"Trek on Sunday?\"}")
			.andExpect(status().isCreated());
		getAs(asha, "/connections").andExpect(jsonPath("$[0].displayName").value("Ravi"))
			.andExpect(jsonPath("$[0].lastMessage").value("Trek on Sunday?"))
			.andExpect(jsonPath("$[0].lastFromMe").value(false));
	}
}
