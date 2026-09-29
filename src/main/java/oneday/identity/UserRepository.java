package oneday.identity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	Optional<User> findByPhone(String phone);

	boolean existsByPhone(String phone);

	List<User> findByVerificationStatusOrderByCreatedAtAsc(VerificationStatus status);
}
