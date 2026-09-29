package oneday.verification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationAttemptRepository extends JpaRepository<VerificationAttempt, String> {

	List<VerificationAttempt> findByUserIdOrderByCreatedAtDesc(String userId);

	void deleteByUserId(String userId);
}
