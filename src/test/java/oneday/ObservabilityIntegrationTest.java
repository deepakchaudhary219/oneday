package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Request ids on every response and log line, bug handling, and the product metrics. */
@Import(ObservabilityIntegrationTest.FailingController.class)
class ObservabilityIntegrationTest extends ApiTestSupport {

	@Autowired
	private MeterRegistry registry;

	@Test
	void everyResponseCarriesARequestIdAndErrorsRepeatIt() throws Exception {
		String asha = register("Asha");
		getAs(asha, "/profile/me").andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
		mvc.perform(get("/profile/me")).andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-Id"));

		// The app's own id is kept, so its logs and ours line up; a malformed one is replaced.
		mvc.perform(get("/grievances/officer").header("X-Request-Id", "app-7f3a9c21"))
			.andExpect(header().string("X-Request-Id", "app-7f3a9c21"));
		mvc.perform(get("/grievances/officer").header("X-Request-Id", "evil\nINFO forged log line"))
			.andExpect(header().string("X-Request-Id", not(containsString("evil"))));

		MvcResult failed = postAs(asha, "/grievances", "{\"category\":\"OTHER\",\"description\":\"\"}")
			.andExpect(status().isBadRequest())
			.andReturn();
		assertThat(com.jayway.jsonpath.JsonPath.<String>read(failed.getResponse().getContentAsString(), "$.requestId"))
			.isEqualTo(failed.getResponse().getHeader("X-Request-Id"));
	}

	@Test
	void aBugIsAnsweredWithoutInternalsButWithTheIdToQuote() throws Exception {
		MvcResult result = getAs(register("Asha"), "/test/boom").andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(content().string(not(containsString("secret-internal-detail"))))
			.andReturn();
		assertThat(com.jayway.jsonpath.JsonPath.<String>read(result.getResponse().getContentAsString(), "$.requestId"))
			.isEqualTo(result.getResponse().getHeader("X-Request-Id"));
	}

	@Test
	void metricsAreForAdminsOnThePublicPortAndCountTheCoreLoop() throws Exception {
		double signalsBefore = count("oneday.signals.sent");
		double revealsBefore = count("oneday.reveals");
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		String signal = sendSignal(ravi, postPublicMoment(asha, "coffee"), null);
		postAs(asha, "/signals/" + signal + "/reveal", null).andExpect(status().isOk());
		assertThat(count("oneday.signals.sent")).isEqualTo(signalsBefore + 1);
		assertThat(count("oneday.reveals")).isEqualTo(revealsBefore + 1);

		mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
		getAs(asha, "/actuator/prometheus").andExpect(status().isForbidden());
		String admin = promote(verifiedUser("Admin"), "ADMIN");
		getAs(admin, "/actuator/prometheus").andExpect(status().isOk())
			.andExpect(content().string(containsString("oneday_signals_sent_total")))
			.andExpect(content().string(containsString("oneday_reveals_total")))
			.andExpect(content().string(containsString("jvm_memory_used_bytes")))
			.andExpect(content().string(not(containsString(userIdOf(asha)))));
	}

	@Test
	void overdueGrievancesShowUpAsAGauge() throws Exception {
		postAs(register("Priya"), "/grievances",
				"{\"category\":\"INTIMATE_IMAGERY\",\"description\":\"Someone posted a private photo of me.\"}")
			.andExpect(status().isCreated());
		assertThat(registry.get("oneday.grievances.overdue").gauge().value()).isZero();
		clock.advance(Duration.ofHours(25));
		assertThat(registry.get("oneday.grievances.overdue").gauge().value()).isEqualTo(1);
		assertThat(registry.get("oneday.reports.overdue").tag("priority", "P0").gauge().value()).isZero();
	}

	private double count(String name) {
		var counter = registry.find(name).counter();
		return counter == null ? 0 : counter.count();
	}

	@RestController
	static class FailingController {

		@GetMapping("/test/boom")
		String boom() {
			throw new IllegalStateException("secret-internal-detail");
		}
	}
}
