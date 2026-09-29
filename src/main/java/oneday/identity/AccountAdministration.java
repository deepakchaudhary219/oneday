package oneday.identity;

import java.time.Clock;
import java.util.List;

import oneday.common.ApiException;

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

	private final Clock clock;

	public AccountAdministration(UserRepository users, Clock clock) {
		this.users = users;
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
			}
		}
		return user;
	}

	/** Suspension takes effect immediately: every service re-checks account status on each request. */
	@Transactional
	public User suspend(String userId) {
		User user = require(userId);
		user.suspend();
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
