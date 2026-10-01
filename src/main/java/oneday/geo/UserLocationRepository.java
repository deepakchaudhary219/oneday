package oneday.geo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserLocationRepository extends JpaRepository<UserLocation, String> {

	/** People sharing a cell inside a bounding box (indexed prefilter; callers refine by distance). */
	@Query("select l.cellLat, l.cellLon from UserLocation l where l.cellLat between :minLat and :maxLat "
			+ "and l.cellLon between :minLon and :maxLon")
	List<Object[]> cellsInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLon") double minLon, @Param("maxLon") double maxLon);
}
