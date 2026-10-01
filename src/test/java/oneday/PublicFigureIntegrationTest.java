package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Public Figures: staff-verified, followed one way, with no public counts and no user ids. */
@SpringBootTest
class PublicFigureIntegrationTest extends ApiTestSupport {

	private static final String APPLICATION = """
			{"handle":"@Kavya.Sings","publicName":"Kavya Menon","category":"MUSICIAN","bio":"Playback singer",
			 "evidence":"https://example.org/kavya-official"}""";

	@Test
	void aVerifiedFigureIsFollowedWithoutPublicMetrics() throws Exception {
		String kavya = verifiedUser("Kavya");
		locate(kavya, BLR_LAT, BLR_LON);
		String fan = verifiedUser("Ravi");
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");

		postAs(kavya, "/figures/apply", APPLICATION).andExpect(status().isCreated())
			.andExpect(jsonPath("$.handle").value("kavya.sings"))
			.andExpect(jsonPath("$.status").value("PENDING"));
		postAs(kavya, "/figures/apply", APPLICATION).andExpect(status().isConflict());
		getAs(fan, "/figures/kavya.sings").andExpect(status().isNotFound()); // not approved yet
		postAs(fan, "/figures/apply", APPLICATION.replace("Kavya Menon", "Fake")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HANDLE_TAKEN"));
		getAs(fan, "/staff/figures").andExpect(status().isForbidden());

		getAs(moderator, "/staff/figures").andExpect(jsonPath("$", hasSize(1)));
		postAs(moderator, "/staff/figures/" + userIdOf(kavya) + "/decision", "{\"decision\":\"APPROVE\",\"note\":\"official site\"}")
			.andExpect(jsonPath("$.status").value("APPROVED"));
		getAs(kavya, "/notices").andExpect(jsonPath("$[0].message").value("You're verified as a Public Figure: @kavya.sings."));

		postAs(fan, "/figures/@kavya.sings/follow", null).andExpect(jsonPath("$.following").value(true))
			.andExpect(jsonPath("$.verified").value(true));
		String profile = body(getAs(fan, "/figures/kavya.sings"));
		assertThat(profile).doesNotContain("followers").doesNotContain(userIdOf(kavya));
		getAs(kavya, "/figures/me").andExpect(jsonPath("$.followers").value(1));

		String story = postPublicMoment(kavya, "music");
		getAs(fan, "/figures/feed").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].handle").value("kavya.sings"))
			.andExpect(jsonPath("$[0].moment.id").value(story))
			.andExpect(jsonPath("$[0].moment.firstName").value("Kavya Menon"))
			.andExpect(jsonPath("$[0].moment.layer").value("FULL"));

		// Blocking from the profile ends the follow; a revoked badge hides the profile.
		postAs(kavya, "/safety/blocks", "{\"figureHandle\":\"kavya.sings\"}").andExpect(status().isUnprocessableContent());
		postAs(fan, "/safety/blocks", "{\"figureHandle\":\"kavya.sings\"}").andExpect(status().isNoContent());
		deliverEvents();
		getAs(fan, "/figures/following").andExpect(jsonPath("$", hasSize(0)));
		postAs(moderator, "/staff/figures/" + userIdOf(kavya) + "/decision", "{\"decision\":\"REVOKE\"}")
			.andExpect(jsonPath("$.status").value("REVOKED"));
		getAs(verifiedUser("Meera"), "/figures/kavya.sings").andExpect(status().isNotFound());
	}
}
