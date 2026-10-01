package oneday.pulsestatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PulseStatusRepository extends JpaRepository<PulseStatus, String> {

	@Query("select s from PulseStatus s where s.id = :statusId")
	Optional<PulseStatus> findByStatusId(@Param("statusId") String statusId);

	@Query("select s from PulseStatus s where s.userId in :userIds and s.expiresAt > :now order by s.setAt desc")
	List<PulseStatus> findLive(@Param("userIds") Collection<String> userIds, @Param("now") Instant now);

	@Modifying
	@Query("delete from PulseStatus s where s.expiresAt <= :now")
	int deleteExpired(@Param("now") Instant now);
}
