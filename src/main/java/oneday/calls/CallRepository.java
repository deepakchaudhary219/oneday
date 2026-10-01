package oneday.calls;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CallRepository extends JpaRepository<Call, String> {

	@Query("select c from Call c where (c.callerId = :userId or c.calleeId = :userId) "
			+ "and c.status <> oneday.calls.Call.Status.ENDED")
	List<Call> findOpenFor(@Param("userId") String userId);

	@Query("select c from Call c where c.status = oneday.calls.Call.Status.RINGING and c.startedAt < :before")
	List<Call> findRingingSince(@Param("before") Instant before);

	@Query("select c from Call c where c.status = oneday.calls.Call.Status.ACTIVE and c.answeredAt < :before")
	List<Call> findActiveSince(@Param("before") Instant before);

	@Query("select c from Call c where c.callerId = :userId or c.calleeId = :userId order by c.startedAt")
	List<Call> findInvolving(@Param("userId") String userId);

	@Query("select c from Call c where c.status <> oneday.calls.Call.Status.ENDED and "
			+ "((c.callerId = :a and c.calleeId = :b) or (c.callerId = :b and c.calleeId = :a))")
	List<Call> findOpenBetween(@Param("a") String a, @Param("b") String b);

	@Modifying
	@Query("delete from Call c where c.status = oneday.calls.Call.Status.ENDED and c.endedAt < :cutoff")
	int purgeEndedBefore(@Param("cutoff") Instant cutoff);

	@Modifying
	@Query("delete from Call c where c.callerId = :userId or c.calleeId = :userId")
	void deleteInvolving(@Param("userId") String userId);
}
