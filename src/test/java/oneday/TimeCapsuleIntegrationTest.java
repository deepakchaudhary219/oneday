package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;

import oneday.capsules.TimeCapsuleService;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Time Capsules: sealed until the day, then opened once, with media that outlives the story bucket. */
@SpringBootTest
class TimeCapsuleIntegrationTest extends ApiTestSupport {

	@Autowired
	TimeCapsuleService capsules;

	@Test
	void aCapsuleStaysSealedUntilItsDayThenOpensWithItsPhoto() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String connection = connect(asha, ravi);
		Instant opens = clock.instant().plus(Duration.ofDays(30));

		postAs(asha, "/capsules", seal(connection, "hi", null, clock.instant().plus(Duration.ofHours(2))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_OPEN_DATE"));
		postAs(asha, "/capsules", seal(connection, "hi", null, clock.instant().plus(Duration.ofDays(6 * 365))))
			.andExpect(status().isBadRequest());
		String photo = upload(asha, "PHOTO", "image/jpeg");
		String id = JsonPath.read(body(postAs(asha, "/capsules", seal(connection, "Open this on our trek anniversary", photo, opens))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.direction").value("SENT"))
			.andExpect(jsonPath("$.message").value("Open this on our trek anniversary"))), "$.id");
		assertThat(deliverEvents()).isGreaterThanOrEqualTo(1); // the durable media copy

		getAs(ravi, "/capsules/incoming").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].state").value("SEALED"))
			.andExpect(jsonPath("$[0].withFirstName").value("Asha"))
			.andExpect(jsonPath("$[0].message").doesNotExist())
			.andExpect(jsonPath("$[0].mediaUrl").doesNotExist());

		// A capsule to yourself is sealed even from you.
		postAs(asha, "/capsules", seal(null, "Dear future me", null, opens)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.direction").value("SELF"))
			.andExpect(jsonPath("$.message").doesNotExist());

		clock.advance(Duration.ofDays(30).plusMinutes(1));
		assertThat(capsules.openDue()).isEqualTo(2);
		asha = signIn(asha); // a month later: fresh sign-ins
		ravi = signIn(ravi);
		getAs(ravi, "/capsules/" + id).andExpect(jsonPath("$.state").value("OPEN"))
			.andExpect(jsonPath("$.message").value("Open this on our trek anniversary"))
			.andExpect(jsonPath("$.mediaUrl", containsString("capsules/")));
		getAs(ravi, "/notices").andExpect(jsonPath("$[0].message", containsString("Asha")));
		deleteAs(asha, "/capsules/" + id).andExpect(status().isConflict());
		getAs(asha, "/capsules/sent").andExpect(jsonPath("$[?(@.direction=='SELF')].message").value(contains("Dear future me")));
		getAs(ravi, "/privacy/export").andExpect(jsonPath("$.timeCapsules", hasSize(1)));
	}

	@Test
	void aBlockOrCancellationStopsACapsuleAndStrangersCannotSeeIt() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String meera = verifiedUser("Meera");
		String connection = connect(asha, ravi);
		Instant opens = clock.instant().plus(Duration.ofDays(2));
		String first = JsonPath.read(body(postAs(asha, "/capsules", seal(connection, "one", null, opens))), "$.id");
		String second = JsonPath.read(body(postAs(asha, "/capsules", seal(connection, "two", null, opens))), "$.id");
		getAs(meera, "/capsules/" + first).andExpect(status().isNotFound());
		deleteAs(ravi, "/capsules/" + first).andExpect(status().isNotFound()); // only the sender cancels
		deleteAs(asha, "/capsules/" + first).andExpect(status().isNoContent());
		getAs(ravi, "/capsules/incoming").andExpect(jsonPath("$", hasSize(1)));

		postAs(ravi, "/safety/blocks", "{\"connectionId\":\"" + connection + "\"}").andExpect(status().isNoContent());
		deliverEvents();
		getAs(ravi, "/capsules/incoming").andExpect(jsonPath("$", hasSize(0)));
		getAs(asha, "/capsules/" + second).andExpect(status().isNotFound());
		postAs(asha, "/capsules", seal(connection, "three", null, opens)).andExpect(status().isConflict());
	}

	private static String seal(String connectionId, String message, String photo, Instant opensAt) {
		return "{%s\"message\":\"%s\",%s\"opensAt\":\"%s\"}".formatted(
				connectionId == null ? "" : "\"connectionId\":\"" + connectionId + "\",", message,
				photo == null ? "" : "\"mediaKind\":\"PHOTO\",\"mediaRef\":\"" + photo + "\",", opensAt);
	}
}
