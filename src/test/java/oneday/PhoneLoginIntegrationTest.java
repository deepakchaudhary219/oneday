package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;

import oneday.sms.DevSmsSender;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Phone OTP login: the default way Indian users sign in. */
class PhoneLoginIntegrationTest extends ApiTestSupport {

	private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

	@Autowired
	private DevSmsSender sms;

	@Test
	void aNewNumberSignsUpThenLogsInWithoutAPassword() throws Exception {
		String national = randomNational();
		String challenge = requestCode(national.substring(0, 5) + " " + national.substring(5));
		String code = lastCode("+91" + national);

		verify(challenge, national, wrong(code), null).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("OTP_INVALID"));
		// A new number needs signup details; the code stays valid so the app can resubmit.
		verify(challenge, national, code, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SIGNUP_DETAILS_REQUIRED"));
		String signup = body(verify(challenge, national, code, adult()).andExpect(status().isOk())
			.andExpect(jsonPath("$.newAccount").value(true))
			.andExpect(jsonPath("$.token.verified").value(false)));
		String token = JsonPath.read(signup, "$.token.token");
		getAs(token, "/profile/me").andExpect(jsonPath("$.displayName").value("Priya"));

		// Codes are single use.
		verify(challenge, national, code, adult()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

		String login = body(verify(requestCode("+91" + national), national, lastCode("+91" + national), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.newAccount").value(false)));
		assertThat(userIdOf(JsonPath.read(login, "$.token.token"))).isEqualTo(userIdOf(token));

		getAs(token, "/privacy/export").andExpect(jsonPath("$.account.phone").value("+91" + national))
			.andExpect(jsonPath("$.account.email").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void codesExpireAndLockAfterTooManyWrongAttempts() throws Exception {
		String national = randomNational();
		String challenge = requestCode(national);
		String code = lastCode("+91" + national);
		for (int i = 0; i < 5; i++) {
			verify(challenge, national, wrong(code), null).andExpect(jsonPath("$.code").value("OTP_INVALID"));
		}
		verify(challenge, national, code, adult()).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

		String second = requestCode(national);
		String secondCode = lastCode("+91" + national);
		clock.advance(Duration.ofMinutes(6));
		verify(second, national, secondCode, adult()).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
	}

	@Test
	void aChallengeOnlyWorksForTheNumberItWasSentTo() throws Exception {
		String national = randomNational();
		String challenge = requestCode(national);
		verify(challenge, randomNational(), lastCode("+91" + national), adult()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("OTP_INVALID"));
	}

	@Test
	void requestsAreRateLimitedPerNumber() throws Exception {
		String national = randomNational();
		for (int i = 0; i < 3; i++) {
			requestCode(national);
		}
		otpRequest(national).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("OTP_RATE_LIMITED"));
		clock.advance(Duration.ofMinutes(16));
		requestCode(national);
	}

	@Test
	void underEighteensCannotSignUpByPhoneEither() throws Exception {
		String national = randomNational();
		String challenge = requestCode(national);
		Map<String, String> minor = Map.of("displayName", "Teen", "dateOfBirth",
				LocalDate.now(clock).minusYears(16).toString(), "consentVersion", "2026-09");
		verify(challenge, national, lastCode("+91" + national), minor).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("UNDER_AGE"));
		String again = requestCode(national);
		verify(again, national, lastCode("+91" + national), null)
			.andExpect(jsonPath("$.code").value("SIGNUP_DETAILS_REQUIRED"));
	}

	@Test
	void suspendedAccountsSignInToARestrictedAccount() throws Exception {
		String national = randomNational();
		String token = JsonPath.read(body(verify(requestCode(national), national, lastCode("+91" + national), adult())),
				"$.token.token");
		jdbc.update("update users set account_status = 'SUSPENDED' where id = ?", userIdOf(token));
		String restricted = JsonPath.read(body(verify(requestCode(national), national, lastCode("+91" + national), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.newAccount").value(false))
			.andExpect(jsonPath("$.token.accountStatus").value("SUSPENDED"))), "$.token.token");
		getAs(restricted, "/privacy/export").andExpect(status().isOk());
		getAs(restricted, "/profile/me").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	@Test
	void neitherTheNumberNorTheCodeIsStoredInTheClear() throws Exception {
		String national = randomNational();
		requestCode(national);
		String code = lastCode("+91" + national);
		List<Map<String, Object>> rows = jdbc.queryForList("select * from otp_challenges");
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).toString()).doesNotContain(national).doesNotContain(code);
	}

	@Test
	void numbersAreNormalisedAndValidated() throws Exception {
		otpRequest("12345").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PHONE"));
		requestCode("+44 7700 900123");
		assertThat(sms.lastMessageTo("+447700900123")).isPresent();
		String national = randomNational();
		requestCode("0" + national);
		assertThat(sms.lastMessageTo("+91" + national)).isPresent();
	}

	// ---- helpers ------------------------------------------------------------------------------------

	private static String randomNational() {
		return "9" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
	}

	private ResultActions otpRequest(String phone) throws Exception {
		return mvc.perform(post("/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"" + phone + "\"}"));
	}

	private String requestCode(String phone) throws Exception {
		return JsonPath.read(body(otpRequest(phone).andExpect(status().isAccepted())), "$.challengeId");
	}

	private String lastCode(String e164) {
		String message = sms.lastMessageTo(e164).orElseThrow();
		Matcher matcher = CODE.matcher(message);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private static String wrong(String code) {
		return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
	}

	private static Map<String, String> adult() {
		return Map.of("displayName", "Priya", "dateOfBirth", "1997-02-14", "consentVersion", "2026-09");
	}

	private ResultActions verify(String challengeId, String phone, String code, Map<String, String> signup)
			throws Exception {
		StringBuilder json = new StringBuilder("{\"challengeId\":\"" + challengeId + "\",\"phone\":\"" + phone
				+ "\",\"code\":\"" + code + "\"");
		if (signup != null) {
			signup.forEach((k, v) -> json.append(",\"").append(k).append("\":\"").append(v).append('"'));
		}
		json.append('}');
		return mvc.perform(post("/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(json.toString()));
	}
}
