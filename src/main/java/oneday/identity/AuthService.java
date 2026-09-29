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
import oneday.security.TokenService;
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

	private final TokenService tokens;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	private final OneDayProperties properties;

	public AuthService(UserRepository users, ProfileService profiles, PasswordEncoder passwordEncoder,
			TokenService tokens, RateLimiter rateLimiter, Clock clock, OneDayProperties properties) {
		this.users = users;
		this.profiles = profiles;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.properties = properties;
	}

	/**
	 * Only a declared DOB is needed to start browsing (blueprint §41.4). Under-18s are refused before
	 * anything is persisted: the platform does not process children's data (DPDP Rules, blueprint §24.1).
	 */
	@Transactional
	public IssuedToken register(RegisterRequest request, String clientIp) {
		checkSignupAllowed(clientIp, request.dateOfBirth(), request.consentVersion());
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
		}
		User user = users.save(User.withEmail(email, passwordEncoder.encode(request.password()), request.dateOfBirth(),
				request.consentVersion(), clock.instant()));
		profiles.create(user.getId(), request.displayName());
		return tokens.issue(user);
	}

	/**
	 * Signup by a phone number the caller has just proven they control (see {@link OtpService}). Runs inside
	 * the OTP transaction and rejects before writing anything, so a refusal must not poison that
	 * transaction (it still has to record the attempt).
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public IssuedToken registerPhone(String phone, LocalDate dateOfBirth, String displayName, String consentVersion,
			String clientIp) {
		checkSignupAllowed(clientIp, dateOfBirth, consentVersion);
		if (users.existsByPhone(phone)) {
			throw ApiException.conflict("PHONE_TAKEN", "An account with this phone number already exists");
		}
		User user = users.save(User.withPhone(phone, dateOfBirth, consentVersion, clock.instant()));
		profiles.create(user.getId(), displayName);
		return tokens.issue(user);
	}

	@Transactional(readOnly = true, noRollbackFor = ApiException.class)
	public java.util.Optional<IssuedToken> loginPhone(String phone) {
		return users.findByPhone(phone).map(user -> {
			if (!user.isActive()) {
				throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account is suspended");
			}
			return tokens.issue(user);
		});
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

	@Transactional(readOnly = true)
	public IssuedToken login(LoginRequest request) {
		User user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
			.filter(u -> u.getPasswordHash() != null && passwordEncoder.matches(request.password(), u.getPasswordHash()))
			.orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect"));
		if (!user.isActive()) {
			throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account is suspended");
		}
		return tokens.issue(user);
	}
}
