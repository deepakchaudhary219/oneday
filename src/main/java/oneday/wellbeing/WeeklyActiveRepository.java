package oneday.wellbeing;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyActiveRepository extends JpaRepository<WeeklyActive, WeeklyActive.Key> {

	@Query("select w.key.userId from WeeklyActive w where w.key.weekStart = :week")
	List<String> findUserIdsByWeek(@Param("week") LocalDate week);

	@Modifying
	@Query("delete from WeeklyActive w where w.key.userId = :userId")
	void deleteByUserId(@Param("userId") String userId);
}
