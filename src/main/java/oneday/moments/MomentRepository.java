package oneday.moments;

import java.time.Instant;
import java.util.Collection;
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

	boolean existsByMediaRef(String mediaRef);

	@Query("select count(m) from Moment m where m.relayRootId in :rootIds")
	long countAllRelayAnswers(@Param("rootIds") Collection<String> rootIds);

	List<Moment> findByExpiresAtBeforeAndKeptFalse(Instant cutoff, Pageable page);

	List<Moment> findByExpiresAtBeforeAndKeptTrueAndCellLatIsNotNull(Instant cutoff, Pageable page);

	List<Moment> findByOwnerIdAndKeptTrueOrderByCreatedAtDesc(String ownerId, Pageable page);

	List<Moment> findByOwnerIdAndKeptTrue(String ownerId);

	List<Moment> findByOwnerIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String ownerId, Instant from, Instant to);

	boolean existsByRelayRootIdAndOwnerId(String relayRootId, String ownerId);

	List<Moment> findByRelayRootIdAndExpiresAtAfterOrderByRelayDepthAscCreatedAtAsc(String relayRootId, Instant now,
			Pageable page);

	@Query("select m.relayRootId, count(m) from Moment m where m.relayRootId in :rootIds and m.expiresAt > :now "
			+ "group by m.relayRootId")
	List<Object[]> countRelays(@Param("rootIds") Collection<String> rootIds, @Param("now") Instant now);

	List<Moment> findByOwnerIdAndPromptKeyAndExpiresAtAfter(String ownerId, String promptKey, Instant now);

	@Modifying
	@Query("delete from Moment m where m.ownerId = :ownerId")
	void deleteByOwner(@Param("ownerId") String ownerId);

	List<Moment> findByOwnerIdInAndShareScopeAndExpiresAtAfterOrderByCreatedAtDesc(Collection<String> ownerIds,
			ShareScope scope, Instant now, Pageable page);

	List<Moment> findByOwnerIdInAndExpiresAtAfterOrderByCreatedAtAsc(Collection<String> ownerIds, Instant now,
			Pageable page);
}
