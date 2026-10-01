package oneday.wellbeing;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WellbeingAnswerRepository extends JpaRepository<WellbeingAnswer, String> {

	boolean existsByUserIdAndCreatedAtAfter(String userId, Instant since);

	List<WellbeingAnswer> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

	List<WellbeingAnswer> findByUserIdOrderByCreatedAtAsc(String userId);

	@Modifying
	@Query("delete from WellbeingAnswer a where a.userId = :userId")
	void deleteByUserId(@Param("userId") String userId);
}
