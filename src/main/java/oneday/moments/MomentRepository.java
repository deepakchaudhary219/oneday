package oneday.moments;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MomentRepository extends JpaRepository<Moment, String> {

	/** Bounding-box prefilter on snapped cell centres; exact radius/band logic runs in the application. */
	@Query("select m from Moment m where m.shareScope = oneday.moments.ShareScope.PUBLIC_DISCOVERY "
			+ "and m.expiresAt > :now and m.cellLat between :minLat and :maxLat "
			+ "and m.cellLon between :minLon and :maxLon order by m.createdAt desc")
	List<Moment> findPublicInBox(@Param("now") Instant now, @Param("minLat") double minLat,
			@Param("maxLat") double maxLat, @Param("minLon") double minLon, @Param("maxLon") double maxLon,
			Pageable page);

	List<Moment> findByOwnerIdAndExpiresAtAfterOrderByCreatedAtDesc(String ownerId, Instant now);

	List<Moment> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

	@Modifying
	@Query("delete from Moment m where m.ownerId = :ownerId")
	void deleteByOwner(@Param("ownerId") String ownerId);
}
