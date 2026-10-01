package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.support.ApiTestSupport;
import oneday.threads.ThreadService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Collaborative Threads: friends-of-a-friend share one thread without ever seeing each other's ids. */
@SpringBootTest
class CollaborativeThreadIntegrationTest extends ApiTestSupport {

	@Autowired
	ThreadService threads;

	@Test
	void friendsShareOneThreadThatEndsAndIsDeleted() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String meera = verifiedUser("Meera");
		String stranger = verifiedUser("Kabir");
		String withRavi = connect(asha, ravi);
		String withMeera = connect(asha, meera);

		String thread = JsonPath.read(body(postAs(asha, "/threads",
				"{\"title\":\"Coorg trek\",\"days\":2,\"inviteConnectionIds\":[\"%s\",\"%s\"]}".formatted(withRavi, withMeera))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.members", hasSize(1)))), "$.id");
		getAs(ravi, "/threads").andExpect(jsonPath("$[0].myStatus").value("INVITED"));
		getAs(ravi, "/threads/" + thread + "/posts").andExpect(status().isNotFound()); // not in yet
		getAs(ravi, "/notices").andExpect(jsonPath("$[0].message", containsString("Coorg trek")));
		postAs(ravi, "/threads/" + thread + "/accept", null).andExpect(jsonPath("$.myStatus").value("IN"));
		postAs(meera, "/threads/" + thread + "/accept", null).andExpect(jsonPath("$.members", hasSize(3)));
		String body = body(getAs(meera, "/threads/" + thread));
		assertThat(body).doesNotContain(userIdOf(ravi)).doesNotContain(userIdOf(asha));
		getAs(stranger, "/threads/" + thread).andExpect(status().isNotFound());

		String photo = upload(ravi, "PHOTO", "image/jpeg");
		postAs(ravi, "/threads/" + thread + "/posts", "{\"kind\":\"PHOTO\",\"mediaRef\":\"" + photo + "\",\"caption\":\"Summit!\"}")
			.andExpect(status().isCreated());
		deliverEvents();
		clock.advance(Duration.ofSeconds(5));
		postAs(meera, "/threads/" + thread + "/posts", "{\"kind\":\"TEXT\",\"caption\":\"you're so stupid for forgetting the tent\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("EMPATHY_CHECK"));
		postAs(meera, "/threads/" + thread + "/posts", "{\"kind\":\"TEXT\",\"caption\":\"Who has the tent?\"}")
			.andExpect(status().isCreated());
		getAs(asha, "/threads/" + thread + "/posts").andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[1].firstName").value("Ravi"))
			.andExpect(jsonPath("$[1].mediaUrl", containsString("threads/")));

		// Meera reports and blocks Ravi from his post: his posts disappear for her, the thread goes on.
		String raviPost = JsonPath.read(body(getAs(meera, "/threads/" + thread + "/posts")), "$[1].id");
		postAs(stranger, "/safety/reports", "{\"threadPostId\":\"" + raviPost + "\",\"category\":\"SPAM\"}")
			.andExpect(status().isNotFound());
		postAs(meera, "/safety/reports", "{\"threadPostId\":\"" + raviPost + "\",\"category\":\"HARASSMENT\",\"alsoBlock\":true}")
			.andExpect(status().isCreated());
		getAs(meera, "/threads/" + thread + "/posts").andExpect(jsonPath("$", hasSize(1)));
		getAs(meera, "/threads/" + thread).andExpect(jsonPath("$.members", hasSize(2)));

		// The creator removes by handle; then the thread ends, turns read-only, and is purged.
		List<String> handles = JsonPath.read(body(getAs(asha, "/threads/" + thread)),
				"$.members[?(@.firstName=='Meera')].handle");
		String meeraHandle = handles.get(0);
		deleteAs(ravi, "/threads/" + thread + "/members/" + meeraHandle).andExpect(status().isForbidden());
		deleteAs(asha, "/threads/" + thread + "/members/" + meeraHandle).andExpect(status().isNoContent());
		getAs(meera, "/threads/" + thread + "/posts").andExpect(status().isNotFound());

		clock.advance(Duration.ofDays(2).plusMinutes(1));
		asha = signIn(asha);
		postAs(asha, "/threads/" + thread + "/posts", "{\"kind\":\"TEXT\",\"caption\":\"late\"}")
			.andExpect(status().isConflict());
		getAs(asha, "/threads/" + thread + "/posts").andExpect(jsonPath("$", hasSize(2)));
		clock.advance(Duration.ofDays(3));
		assertThat(threads.purgeEnded()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from thread_posts", Integer.class)).isZero();
	}

	@Test
	void onlyYourOwnConnectionsCanBeInvitedAndAThreadHoldsTwelve() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String meera = verifiedUser("Meera");
		String withRavi = connect(asha, ravi);
		connect(ravi, meera);
		postAs(meera, "/threads", "{\"title\":\"x\",\"inviteConnectionIds\":[\"" + withRavi + "\"]}")
			.andExpect(status().isNotFound());
		postAs(asha, "/threads", "{\"title\":\"x\",\"days\":9}").andExpect(status().isBadRequest());
		postAs(asha, "/threads", "{\"title\":\"\"}").andExpect(status().isBadRequest());
	}
}
