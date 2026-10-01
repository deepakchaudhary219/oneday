package oneday.dates;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingPointRepository extends JpaRepository<MeetingPoint, String> {

	@Query("select m from MeetingPoint m where m.active = true and m.lat between :minLat and :maxLat "
			+ "and m.lon between :minLon and :maxLon")
	List<MeetingPoint> findActiveInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLon") double minLon, @Param("maxLon") double maxLon);
}
