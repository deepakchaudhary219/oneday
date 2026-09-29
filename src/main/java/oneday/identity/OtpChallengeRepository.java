package oneday.identity;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {

	@Modifying
	@Query("delete from OtpChallenge c where c.createdAt < :cutoff")
	int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
