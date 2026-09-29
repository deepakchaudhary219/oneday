package oneday.staff;

import java.util.Optional;

import oneday.common.ApiException;
import oneday.identity.UserGuard;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is staff. Used when issuing tokens (scopes) and, on every staff request, as a database re-check so
 * a revoked role stops working before the token expires.
 */
@Component
public class StaffDirectory {

	private final StaffRepository staff;

	private final UserGuard guard;

	private final StaffProperties properties;

	public StaffDirectory(StaffRepository staff, UserGuard guard, StaffProperties properties) {
		this.staff = staff;
		this.guard = guard;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public Optional<StaffRole> roleOf(String userId) {
		if (properties.bootstrapAdminUserIds().contains(userId)) {
			return Optional.of(StaffRole.ADMIN);
		}
		return staff.findById(userId).map(StaffMember::getRole);
	}

	/** Erasure: a deleted account holds no role (audit rows about past actions are retained). */
	@Transactional
	public void forget(String userId) {
		if (staff.existsById(userId)) {
			staff.deleteById(userId);
		}
	}

	/** Staff must be active, liveness-verified accounts holding at least {@code required}. */
	@Transactional(readOnly = true)
	public void require(String userId, StaffRole required) {
		guard.requireContactAllowed(userId);
		if (roleOf(userId).filter(role -> role.covers(required)).isEmpty()) {
			throw ApiException.forbidden("STAFF_ONLY", "This action is restricted to Trust & Safety staff");
		}
	}
}
