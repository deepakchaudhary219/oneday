package oneday.prompts;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalSeasonRepository extends JpaRepository<FestivalSeason, String> {

	@Query("select f from FestivalSeason f where f.startsOn <= :date and f.endsOn >= :date order by f.startsOn desc")
	List<FestivalSeason> findActiveOn(@Param("date") LocalDate date);

	List<FestivalSeason> findByEndsOnGreaterThanEqualOrderByStartsOnAsc(LocalDate from);
}
