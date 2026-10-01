package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * The API contract for the apps. {@code docs/api/openapi.json} is generated from the code; this test fails when
 * they drift, so a change to the API is always a visible change to the contract. To accept a change:
 * {@code ONEDAY_UPDATE_OPENAPI=1 ./mvnw test -Dtest=OpenApiContractTest}.
 */
@SpringBootTest
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiContractTest extends ApiTestSupport {

	private static final Path SPEC = Path.of("docs/api/openapi.json");

	@Test
	void theCheckedInContractMatchesTheCode() throws Exception {
		String raw = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
		JsonNode spec = json.readTree(raw);
		// The server list depends on how the test reaches the app; the apps configure their own base URL.
		((ObjectNode) spec).remove("servers");
		String generated = json.writeValueAsString(spec) + "\n";

		assertThat(spec.path("paths").has("/auth/register")).isTrue();
		assertThat(spec.path("paths").has("/e2ee/devices")).isTrue();
		assertThat(spec.path("paths").has("/live")).isTrue();
		assertThat(generated).doesNotContain("\"jwt\"").as("@AuthenticationPrincipal must not leak into the contract");

		if (System.getenv("ONEDAY_UPDATE_OPENAPI") != null || !Files.exists(SPEC)) {
			Files.createDirectories(SPEC.getParent());
			Files.writeString(SPEC, generated);
		}
		assertThat(Files.readString(SPEC))
			.as("docs/api/openapi.json is out of date. Run: ONEDAY_UPDATE_OPENAPI=1 ./mvnw test -Dtest=OpenApiContractTest")
			.isEqualTo(generated);
	}
}
