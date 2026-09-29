package oneday.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Locale;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.config.OneDayProperties;
import oneday.profile.ProfileService;
import oneday.security.SessionService;
import oneday.security.TokenService.IssuedToken;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	static final int ADULT_AGE = 18;

	private final UserRepository users;

	private final ProfileService profiles;

	private final PasswordEncoder passwordEncoder;

	private final SessionService sessions;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	private final OneDayProperties properties;

	public AuthService(UserRepository users, ProfileService profiles, PasswordEncoder passwordEncoder,
			SessionService sessions, RateLimiter rateLimiter, Clock clock, OneDayProperties properties) {
		this.users = users;
		this.profiles = profiles;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.properties = properties;
	}

	/**
	 * Only a declared DOB is needed to start browsing (blueprint §41.4). Under-18s are refused before
	 * anything is persisted: the platform does not process children's data (DPDP Rules, blueprint §24.1).
	 */
	@Transactional
	public IssuedToken register(RegisterRequest request, ClientInfo client) {
		checkSignupAllowed(client.ip(), request.dateOfBirth(), request.consentVersion());
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
		}
		User user = users.save(User.withEmail(email, passwordEncoder.encode(request.password()), request.dateOfBirth(),
				request.consentVersion(), clock.instant()));
		profiles.create(user.getId(), request.displayName());
		return sessions.start(user, client.device());
	}

	/**
	 * Signup by a phone number the caller has just proven they control (see {@link OtpService}). Runs inside
	 * the OTP transaction and rejects before writing anything, so a refusal must not poison that
	 * transaction (it still has to record the attempt).
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public IssuedToken registerPhone(String phone, LocalDate dateOfBirth, String displayName, String consentVersion,
			ClientInfo client) {
		checkSignupAllowed(client.ip(), dateOfBirth, consentVersion);
		if (users.existsByPhone(phone)) {
			throw ApiException.conflict("PHONE_TAKEN", "An account with this phone number already exists");
		}
		User user = users.save(User.withPhone(phone, dateOfBirth, consentVersion, clock.instant()));
		profiles.create(user.getId(), displayName);
		return sessions.start(user, client.device());
	}

	/** A suspended account may sign in: it keeps its data rights and can appeal (see {@link #login}). */
	@Transactional(noRollbackFor = ApiException.class)
	public java.util.Optional<IssuedToken> loginPhone(String phone, ClientInfo client) {
		return users.findByPhone(phone).map(user -> sessions.start(user, client.device()));
	}

	private void checkSignupAllowed(String clientIp, LocalDate dateOfBirth, String consentVersion) {
		if (!rateLimiter.tryAcquire("register:" + clientIp, properties.registration().perIpPerHour(),
				Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("SIGNUP_RATE_LIMITED", "Too many signups from this network, try later");
		}
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		if (dateOfBirth.isAfter(today) || Period.between(dateOfBirth, today).getYears() > 120) {
			throw ApiException.badRequest("INVALID_DATE_OF_BIRTH", "Please enter a real date of birth");
		}
		if (Period.between(dateOfBirth, today).getYears() < ADULT_AGE) {
			throw ApiException.unprocessable("UNDER_AGE", "OneDay is only for adults aged 18 and over");
		}
		if (!properties.registration().currentConsentVersion().equals(consentVersion)) {
			throw ApiException.badRequest("CONSENT_OUTDATED", "Please review and accept the current privacy notice");
		}
	}

	@Transactional
	public void deleteAccount(String userId) {
		users.deleteById(userId);
	}

	/**
	 * Password guessing is limited per network and per account. A suspended account may still sign in: its
	 * token carries no contact scopes and every feature re-checks the account, but the holder can export their
	 * data, read why they were suspended and appeal. Accounts awaiting erasure have no email left to match.
	 */
	@Transactional
	public IssuedToken login(LoginRequest request, ClientInfo client) {
		OneDayProperties.Security limits = properties.security();
		if (!rateLimiter.tryAcquire("login-ip:" + client.ip(), limits.loginsPerIpPerHour(), Duration.ofHours(1))) {
			throw tooManyLogins();
		}
		User user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
		if (user != null && !rateLimiter.tryAcquire("login-account:" + user.getId(), limits.loginsPerAccountPerHour(),
				Duration.ofHours(1))) {
			throw tooManyLogins();
		}
		if (user == null || user.getPasswordHash() == null
				|| !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect");
		}
		return sessions.start(user, client.device());
	}

	private static ApiException tooManyLogins() {
		return ApiException.tooManyRequests("LOGIN_RATE_LIMITED", "Too many sign-in attempts. Try again later.");
	}
}
