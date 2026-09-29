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
		if (!rateLimiter.tryAcquire("register:" + clientIp, properties.registration().perIpPerHour(),
				Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("SIGNUP_RATE_LIMITED", "Too many signups from this network, try later");
		}
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		if (request.dateOfBirth().isAfter(today) || Period.between(request.dateOfBirth(), today).getYears() > 120) {
			throw ApiException.badRequest("INVALID_DATE_OF_BIRTH", "Please enter a real date of birth");
		}
		if (Period.between(request.dateOfBirth(), today).getYears() < ADULT_AGE) {
			throw ApiException.unprocessable("UNDER_AGE", "OneDay is only for adults aged 18 and over");
		}
		if (!properties.registration().currentConsentVersion().equals(request.consentVersion())) {
			throw ApiException.badRequest("CONSENT_OUTDATED", "Please review and accept the current privacy notice");
		}
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
		}
		User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.dateOfBirth(),
				request.consentVersion(), clock.instant()));
		profiles.create(user.getId(), request.displayName());
		return tokens.issue(user);
	}

	@Transactional
	public void deleteAccount(String userId) {
		users.deleteById(userId);
	}

	@Transactional(readOnly = true)
	public IssuedToken login(LoginRequest request) {
		User user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
			.filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
			.orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect"));
		if (!user.isActive()) {
			throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account is suspended");
		}
		return tokens.issue(user);
	}
}
