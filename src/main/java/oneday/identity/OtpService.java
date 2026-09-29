package oneday.identity;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.config.OneDayProperties;
import oneday.security.TokenService.IssuedToken;
import oneday.sms.SmsSender;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phone login with one-time codes, the way most Indian users expect to sign in.
 *
 * <ul>
 * <li>Requesting a code looks the same whether or not the number has an account (no enumeration).</li>
 * <li>Codes and numbers are stored only as keyed HMACs; comparison is constant-time.</li>
 * <li>Codes expire, allow a few attempts, and are rate-limited per number and per network.</li>
 * <li>New accounts go through the same 18+ and consent checks as email signup.</li>
 * </ul>
 */
@Service
public class OtpService {

	private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");

	private final OtpChallengeRepository challenges;

	private final ObjectProvider<SmsSender> sms;

	private final AuthService auth;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	private final OtpProperties settings;

	private final SecretKeySpec key;

	private final SecureRandom random = new SecureRandom();

	public OtpService(OtpChallengeRepository challenges, ObjectProvider<SmsSender> sms, AuthService auth,
			RateLimiter rateLimiter, Clock clock, OtpProperties settings, OneDayProperties properties) {
		this.challenges = challenges;
		this.sms = sms;
		this.auth = auth;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.settings = settings;
		this.key = new SecretKeySpec(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Transactional
	public ChallengeView request(String rawPhone, String clientIp) {
		String phone = normalize(rawPhone);
		SmsSender sender = sms.getIfAvailable();
		if (sender == null) {
			throw ApiException.unavailable("SMS_UNAVAILABLE", "Phone login is temporarily unavailable");
		}
		String phoneHash = hmac("phone|" + phone);
		if (!rateLimiter.tryAcquire("otp-phone:" + phoneHash, settings.perPhoneLimit(), settings.perPhoneWindow())
				|| !rateLimiter.tryAcquire("otp-ip:" + clientIp, settings.perIpPerHour(), Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("OTP_RATE_LIMITED", "Please wait a few minutes before asking again");
		}
		String code = "%06d".formatted(random.nextInt(1_000_000));
		Instant now = clock.instant();
		OtpChallenge challenge = challenges
			.save(new OtpChallenge(phoneHash, hmac("code|" + phone + "|" + code), now, now.plus(settings.ttl())));
		sender.send(phone, "Your OneDay code is " + code + ". It expires in " + settings.ttl().toMinutes()
				+ " minutes. Never share it with anyone.");
		return new ChallengeView(challenge.getId(), challenge.getExpiresAt());
	}

	/**
	 * Logs in an existing account, or creates one when signup details are supplied. When a new number
	 * arrives without details the code is <em>not</em> consumed, so the app can ask for them and resubmit.
	 * Failed attempts are persisted even though the request fails.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public PhoneAuthResult verify(String challengeId, String rawPhone, String code, SignupDetails signup,
			ClientInfo client) {
		String phone = normalize(rawPhone);
		Instant now = clock.instant();
		OtpChallenge challenge = challenges.findById(challengeId)
			.filter(c -> c.getPhoneHash().equals(hmac("phone|" + phone)))
			.orElseThrow(OtpService::invalidCode);
		if (!challenge.isUsable(now, settings.maxAttempts())) {
			throw ApiException.unauthorized("OTP_EXPIRED", "This code has expired. Request a new one.");
		}
		byte[] expected = challenge.getCodeHash().getBytes(StandardCharsets.UTF_8);
		byte[] actual = hmac("code|" + phone + "|" + (code == null ? "" : code.trim())).getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, actual)) {
			challenge.recordFailedAttempt();
			throw invalidCode();
		}
		Optional<IssuedToken> existing = auth.loginPhone(phone, client);
		if (existing.isPresent()) {
			challenge.consume(now);
			return new PhoneAuthResult(false, existing.get());
		}
		if (signup == null || !signup.isComplete()) {
			throw ApiException.conflict("SIGNUP_DETAILS_REQUIRED",
					"New here! Add your name, date of birth and accept the privacy notice to finish signing up.");
		}
		IssuedToken token = auth.registerPhone(phone, signup.dateOfBirth(), signup.displayName().trim(),
				signup.consentVersion(), client);
		challenge.consume(now);
		return new PhoneAuthResult(true, token);
	}

	/** Codes are short-lived; keep the table small. */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
	@Transactional
	public void sweep() {
		challenges.deleteCreatedBefore(clock.instant().minus(Duration.ofDays(1)));
	}

	/** E.164 normalisation; bare 10-digit and 0-prefixed national numbers get the default country code. */
	String normalize(String raw) {
		String digits = raw == null ? "" : raw.replaceAll("[\\s\\-().]", "");
		if (digits.startsWith("00")) {
			digits = "+" + digits.substring(2);
		}
		else if (digits.matches("0\\d{10}")) {
			digits = settings.defaultCountryCode() + digits.substring(1);
		}
		else if (digits.matches("\\d{10}")) {
			digits = settings.defaultCountryCode() + digits;
		}
		if (!E164.matcher(digits).matches()) {
			throw ApiException.badRequest("INVALID_PHONE", "Enter a mobile number, e.g. 98765 43210 or +91 98765 43210");
		}
		return digits;
	}

	private String hmac(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(("otp|" + value).getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 unavailable", ex);
		}
	}

	private static ApiException invalidCode() {
		return ApiException.unauthorized("OTP_INVALID", "That code isn't right");
	}

	public record ChallengeView(String challengeId, Instant expiresAt) {
	}

	public record SignupDetails(String displayName, LocalDate dateOfBirth, String consentVersion) {

		boolean isComplete() {
			return displayName != null && !displayName.isBlank() && dateOfBirth != null && consentVersion != null;
		}
	}

	/** {@code newAccount} tells the app whether to show onboarding. */
	public record PhoneAuthResult(boolean newAccount, IssuedToken token) {
	}
}
