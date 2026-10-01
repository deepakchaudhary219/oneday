package oneday.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, String> {

	/** Refreshes of one session are serialised so two concurrent rotations can't both succeed. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Session s where s.id = :id")
	Optional<Session> findForUpdate(@Param("id") String id);

	boolean existsByIdAndEndedAtIsNullAndExpiresAtAfter(String id, Instant now);

	@Query("select s from Session s where s.userId = :userId and s.endedAt is null and s.expiresAt > :now "
			+ "order by s.lastUsedAt desc")
	List<Session> findActive(@Param("userId") String userId, @Param("now") Instant now);

	List<Session> findByUserIdOrderByCreatedAtDesc(String userId);

	void deleteByUserId(String userId);

	@Modifying
	@Query("delete from Session s where (s.endedAt is not null and s.endedAt < :cutoff) or s.expiresAt < :cutoff")
	int purgeEndedBefore(@Param("cutoff") Instant cutoff);
}
