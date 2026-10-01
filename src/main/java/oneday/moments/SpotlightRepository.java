package oneday.moments;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpotlightRepository extends JpaRepository<Spotlight, String> {

	List<Spotlight> findByRootMomentIdAndStatus(String rootMomentId, Spotlight.Status status);

	long countByRootMomentIdAndStatusNot(String rootMomentId, Spotlight.Status status);

	Optional<Spotlight> findByReplyMomentId(String replyMomentId);

	List<Spotlight> findByReplierIdAndStatus(String replierId, Spotlight.Status status);

	@Modifying
	@Query("delete from Spotlight s where not exists (select m.id from Moment m where m.id = s.rootMomentId) "
			+ "or not exists (select m.id from Moment m where m.id = s.replyMomentId)")
	int deleteOrphans();

	@Modifying
	@Query("delete from Spotlight s where s.ownerId = :userId or s.replierId = :userId")
	void deleteInvolving(@Param("userId") String userId);
}
