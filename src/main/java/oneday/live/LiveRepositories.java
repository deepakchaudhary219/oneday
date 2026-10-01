package oneday.live;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LiveSessionRepository extends JpaRepository<LiveSession, String> {

	@Query("select s from LiveSession s where s.endedAt is null and s.hostId in :hostIds order by s.startedAt desc")
	List<LiveSession> findLiveBy(@Param("hostIds") Collection<String> hostIds);

	@Query("select s from LiveSession s where s.endedAt is null and s.threadId in :threadIds order by s.startedAt desc")
	List<LiveSession> findLiveInThreads(@Param("threadIds") Collection<String> threadIds);

	@Query("select s from LiveSession s where s.endedAt is null and s.hostId = :hostId")
	List<LiveSession> findLiveOf(@Param("hostId") String hostId);

	@Query("select s from LiveSession s where s.endedAt is null and s.startedAt < :before")
	List<LiveSession> findLiveSince(@Param("before") Instant before);

	List<LiveSession> findByHostId(String hostId);

	@Query("select s.id from LiveSession s where s.endedAt < :cutoff")
	List<String> findIdsEndedBefore(@Param("cutoff") Instant cutoff);
}

interface LiveViewerRepository extends JpaRepository<LiveViewer, LiveViewer.Key> {

	@Query("select count(v) from LiveViewer v where v.key.sessionId = :sessionId")
	long countFor(@Param("sessionId") String sessionId);

	@Modifying
	@Query("delete from LiveViewer v where v.key.sessionId in :sessionIds")
	void deleteForSessions(@Param("sessionIds") Collection<String> sessionIds);

	@Modifying
	@Query("delete from LiveViewer v where v.key.userId = :userId")
	void deleteByUser(@Param("userId") String userId);
}
