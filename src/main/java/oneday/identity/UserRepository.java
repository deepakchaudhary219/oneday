package oneday.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	Optional<User> findByPhone(String phone);

	boolean existsByPhone(String phone);

	List<User> findByVerificationStatusOrderByCreatedAtAsc(VerificationStatus status);

	/** Verified accounts whose last check is older than {@code before} (re-verification is due). */
	List<User> findTop500ByVerificationStatusAndVerifiedAtBefore(VerificationStatus status, Instant before);

	/** Verified accounts approaching the deadline that have not been reminded yet. */
	List<User> findTop500ByVerificationStatusAndVerifiedAtBeforeAndReverifyRemindedAtIsNull(VerificationStatus status,
			Instant before);

	List<User> findByErasureRequestedAtIsNotNullOrderByErasureRequestedAtAsc();
}
