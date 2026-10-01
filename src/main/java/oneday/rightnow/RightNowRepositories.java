package oneday.rightnow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RightNowSessionRepository extends JpaRepository<RightNowSession, String> {

	@Query("select s from RightNowSession s where s.endedAt is null and s.endsAt > :now")
	List<RightNowSession> findActive(@Param("now") Instant now);

	@Query("select s from RightNowSession s where s.userId = :userId and s.endedAt is null and s.endsAt > :now")
	Optional<RightNowSession> findActiveFor(@Param("userId") String userId, @Param("now") Instant now);

	List<RightNowSession> findByUserId(String userId);

	@Modifying
	@Query("delete from RightNowSession s where s.userId = :userId")
	void deleteByUserId(@Param("userId") String userId);
}

interface RightNowJoinRepository extends JpaRepository<RightNowJoin, String> {

	boolean existsBySessionIdAndJoinerId(String sessionId, String joinerId);

	long countByJoinerIdAndCreatedAtAfter(String joinerId, Instant since);

	List<RightNowJoin> findBySessionIdAndStatus(String sessionId, RightNowJoin.Status status);

	List<RightNowJoin> findByJoinerIdAndSessionIdIn(String joinerId, Collection<String> sessionIds);

	@Modifying
	@Query("delete from RightNowJoin j where j.joinerId = :userId or j.sessionId in :sessionIds")
	void deleteInvolving(@Param("userId") String userId, @Param("sessionIds") Collection<String> sessionIds);
}
