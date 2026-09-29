package oneday.identity;

import java.time.Clock;
import java.util.List;

import oneday.common.ApiException;
import oneday.security.Session.EndReason;
import oneday.security.SessionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account state changes made by Trust & Safety staff (never by the account holder). */
@Service
public class AccountAdministration {

	public enum VerificationDecision {
		/** The person is a real adult: unlock contact features. */
		APPROVE,
		/** Inconclusive: let them take the liveness check again. */
		RETRY,
		/** Not verifiable as an adult (e.g. a likely minor): reject and suspend. */
		REJECT
	}

	private final UserRepository users;

	private final SessionService sessions;

	private final Clock clock;

	public AccountAdministration(UserRepository users, SessionService sessions, Clock clock) {
		this.users = users;
		this.sessions = sessions;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<User> awaitingReview() {
		return users.findByVerificationStatusOrderByCreatedAtAsc(VerificationStatus.MANUAL_REVIEW)
			.stream()
			.filter(u -> !u.isDeactivated())
			.toList();
	}

	@Transactional
	public void deactivateForErasure(String userId) {
		require(userId).deactivateForErasure(clock.instant());
		sessions.endAll(userId, EndReason.ERASURE);
	}

	@Transactional(readOnly = true)
	public List<User> pendingErasures() {
		return users.findByErasureRequestedAtIsNotNullOrderByErasureRequestedAtAsc();
	}

	@Transactional(readOnly = true)
	public User require(String userId) {
		return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
	}

	@Transactional
	public User decideVerification(String userId, VerificationDecision decision) {
		User user = require(userId);
		if (user.getVerificationStatus() != VerificationStatus.MANUAL_REVIEW) {
			throw ApiException.conflict("NOT_UNDER_REVIEW", "This account is not awaiting manual review");
		}
		switch (decision) {
			case APPROVE -> user.markVerified(clock.instant());
			case RETRY -> user.resetVerification();
			case REJECT -> {
				user.markRejected();
				user.suspend();
				sessions.endAll(userId, EndReason.SUSPENDED);
			}
		}
		return user;
	}

	/**
	 * Suspension takes effect immediately: every service re-checks account status on each request, and every
	 * session ends, so a device someone else controls is cut off too. The holder can sign in again to a
	 * restricted account (export, notices, appeal).
	 */
	@Transactional
	public User suspend(String userId) {
		User user = require(userId);
		user.suspend();
		sessions.endAll(userId, EndReason.SUSPENDED);
		return user;
	}

	@Transactional
	public User reinstate(String userId) {
		User user = require(userId);
		if (user.isDeactivated()) {
			throw ApiException.conflict("ERASURE_PENDING", "The holder asked for this account to be erased");
		}
		user.reinstate();
		return user;
	}
}
