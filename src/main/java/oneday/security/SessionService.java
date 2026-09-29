package oneday.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import oneday.common.ApiException;
import oneday.config.OneDayProperties;
import oneday.identity.User;
import oneday.identity.UserRepository;
import oneday.notify.Notice;
import oneday.notify.NotificationService;
import oneday.security.Session.EndReason;
import oneday.security.TokenService.IssuedToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in sessions with rotating refresh tokens. A refresh token is {@code <sessionId>.<secret>}; only a
 * SHA-256 of the secret is stored, and every refresh replaces it. Presenting a replaced secret means two
 * parties hold the session, so it is ended for both (reuse detection), except within a short grace period
 * where it is taken for a client retrying a lost response.
 */
@Service
public class SessionService {

	/** Ended sessions are kept this long for the holder's export and for investigating takeovers. */
	static final Duration RETENTION = Duration.ofDays(30);

	private static final Logger log = LoggerFactory.getLogger(SessionService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int MAX_DEVICE_LENGTH = 80;

	private final SessionRepository sessions;

	private final UserRepository users;

	private final TokenService tokens;

	private final NotificationService notifications;

	private final Clock clock;

	private final OneDayProperties.Security settings;

	public SessionService(SessionRepository sessions, UserRepository users, TokenService tokens,
			NotificationService notifications, Clock clock, OneDayProperties properties) {
		this.sessions = sessions;
		this.users = users;
		this.tokens = tokens;
		this.notifications = notifications;
		this.clock = clock;
		this.settings = properties.security();
	}

	/** A new sign-in on a device: a session, an access token and the session's first refresh token. */
	@Transactional
	public IssuedToken start(User user, String device) {
		Instant now = clock.instant();
		String secret = newSecret();
		Session session = sessions.save(new Session(user.getId(), hash(secret), trimDevice(device), now,
				expiry(now, now)));
		List<Session> active = sessions.findActive(user.getId(), now)
			.stream()
			.filter(s -> !s.getId().equals(session.getId()))
			.toList();
		if (active.size() > settings.maxSessions() - 1) {
			active.subList(settings.maxSessions() - 1, active.size()).forEach(s -> s.end(EndReason.DEVICE_LIMIT, now));
		}
		return tokens.issue(user, session.getId(), session.getId() + "." + secret);
	}

	/** A fresh access token in the same session, e.g. after verification changed what the account may do. */
	@Transactional(readOnly = true)
	public IssuedToken reissue(User user, String sessionId) {
		return tokens.issue(user, sessionId, null);
	}

	/**
	 * Swaps a refresh token for a new access token and a new refresh token. Scopes are recomputed, so a
	 * verification or staff-role change applies from here on. Ending a session on reuse must survive the
	 * failed request, hence {@code noRollbackFor}.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public IssuedToken refresh(String refreshToken) {
		Instant now = clock.instant();
		int dot = refreshToken == null ? -1 : refreshToken.indexOf('.');
		if (dot <= 0 || dot == refreshToken.length() - 1) {
			throw invalidRefreshToken();
		}
		Session session = sessions.findForUpdate(refreshToken.substring(0, dot))
			.filter(s -> s.isActive(now))
			.orElseThrow(SessionService::invalidRefreshToken);
		String presented = hash(refreshToken.substring(dot + 1));
		boolean retry = false;
		if (!session.isCurrent(presented)) {
			retry = session.isRetryOfLastRotation(presented, now, settings.refreshReuseGrace());
			if (!retry) {
				session.end(EndReason.REUSE_DETECTED, now);
				log.warn("Refresh token reuse: ended session {}", session.getId());
				notifications.notice(session.getUserId(), Notice.Kind.SECURITY,
						"We signed out one of your devices because its sign-in was used from two places at once. "
								+ "If that wasn't you, sign out everywhere and sign in again.");
				throw invalidRefreshToken();
			}
		}
		User user = users.findById(session.getUserId())
			.filter(u -> !u.isDeactivated())
			.orElseThrow(SessionService::invalidRefreshToken);
		String secret = newSecret();
		session.rotate(hash(secret), retry, now, expiry(session.getCreatedAt(), now));
		return tokens.issue(user, session.getId(), session.getId() + "." + secret);
	}

	@Transactional(readOnly = true)
	public List<SessionView> list(String userId, String currentSessionId) {
		return sessions.findActive(userId, clock.instant())
			.stream()
			.map(s -> new SessionView(s.getId(), s.getDevice(), s.getCreatedAt(), s.getLastUsedAt(),
					s.getId().equals(currentSessionId)))
			.toList();
	}

	@Transactional
	public void signOut(String userId, String sessionId) {
		sessions.findById(sessionId)
			.filter(s -> s.getUserId().equals(userId))
			.ifPresent(s -> s.end(EndReason.SIGNED_OUT, clock.instant()));
	}

	/** Signs out another of the holder's devices, e.g. a lost phone. */
	@Transactional
	public void signOutDevice(String userId, String sessionId) {
		Session session = sessions.findById(sessionId)
			.filter(s -> s.getUserId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("Session"));
		session.end(EndReason.SIGNED_OUT_ELSEWHERE, clock.instant());
	}

	@Transactional
	public void signOutEverywhere(String userId) {
		endAll(userId, EndReason.SIGNED_OUT_ELSEWHERE);
	}

	/** Suspension and erasure end every session of the account. */
	@Transactional
	public void endAll(String userId, EndReason reason) {
		Instant now = clock.instant();
		sessions.findActive(userId, now).forEach(s -> s.end(reason, now));
	}

	@Transactional(readOnly = true)
	public List<Session> history(String userId) {
		return sessions.findByUserIdOrderByCreatedAtDesc(userId);
	}

	/** Erasure. */
	@Transactional
	public void forget(String userId) {
		sessions.deleteByUserId(userId);
	}

	@Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT10M")
	@Transactional
	public int purge() {
		return sessions.purgeEndedBefore(clock.instant().minus(RETENTION));
	}

	/** Idle expiry slides with each refresh but never past the session's maximum age. */
	private Instant expiry(Instant createdAt, Instant now) {
		Instant idle = now.plus(settings.refreshTokenTtl());
		Instant max = createdAt.plus(settings.sessionMaxAge());
		return idle.isBefore(max) ? idle : max;
	}

	private static String newSecret() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** The secret is 256 random bits, so a plain SHA-256 is enough; there is nothing to brute-force. */
	private static String hash(String secret) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String trimDevice(String device) {
		if (device == null || device.isBlank()) {
			return null;
		}
		String clean = device.strip().replaceAll("\\p{Cntrl}", "");
		return clean.length() > MAX_DEVICE_LENGTH ? clean.substring(0, MAX_DEVICE_LENGTH) : clean;
	}

	private static ApiException invalidRefreshToken() {
		return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Please sign in again");
	}

	public record SessionView(String id, String device, Instant createdAt, Instant lastUsedAt, boolean current) {
	}
}
