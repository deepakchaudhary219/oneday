package oneday.identity;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import oneday.common.ApiException;

import org.springframework.stereotype.Component;

/**
 * Service-level re-check behind the filter-chain gate: reads the current account and verification
 * state from the database so suspensions and revoked verification take effect before the token expires.
 */
@Component
public class UserGuard {

	private final UserRepository users;

	public UserGuard(UserRepository users) {
		this.users = users;
	}

	public User requireActive(String userId) {
		User user = requireExisting(userId);
		if (!user.isActive()) {
			throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account is suspended");
		}
		return user;
	}

	/**
	 * The account exists from its holder's point of view: suspended accounts pass (they keep data rights),
	 * accounts deactivated for erasure answer exactly like deleted ones.
	 */
	public User requireExisting(String userId) {
		return users.findById(userId)
			.filter(u -> !u.isDeactivated())
			.orElseThrow(() -> ApiException.unauthorized("ACCOUNT_NOT_FOUND", "Account no longer exists"));
	}

	/** The subset of {@code userIds} whose accounts are active and verified (safe to show or reach). */
	public Set<String> reachableAmong(Collection<String> userIds) {
		return users.findAllById(userIds)
			.stream()
			.filter(u -> u.isActive() && u.isVerified())
			.map(User::getId)
			.collect(Collectors.toSet());
	}

	public User requireContactAllowed(String userId) {
		User user = requireActive(userId);
		if (user.getVerificationStatus() == VerificationStatus.EXPIRED) {
			throw ApiException.forbidden("REVERIFICATION_REQUIRED",
					"It's been a while. A quick liveness re-check keeps everyone here real.");
		}
		if (!user.isVerified()) {
			throw ApiException.forbidden("VERIFICATION_REQUIRED",
					"Complete a quick liveness check before reaching other people");
		}
		return user;
	}
}
