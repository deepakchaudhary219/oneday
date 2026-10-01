package oneday.events;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

	/** Candidates for this relay pass, oldest first (UUIDv7 ids are time-ordered). */
	@Query("select e.id from OutboxEvent e where e.status = oneday.events.OutboxEvent.Status.PENDING "
			+ "and e.nextAttemptAt <= :now and (e.lockedUntil is null or e.lockedUntil < :now) order by e.id")
	List<String> findDue(@Param("now") Instant now, Pageable page);

	/**
	 * Takes a lease on one event. Exactly one replica's update matches, so each event is worked on by one relay
	 * at a time; a relay that dies simply lets its lease run out. Portable to H2 and MySQL alike (no
	 * {@code SKIP LOCKED} needed).
	 */
	@Modifying(clearAutomatically = true)
	@Query("update OutboxEvent e set e.lockedBy = :node, e.lockedUntil = :until where e.id = :id "
			+ "and e.status = oneday.events.OutboxEvent.Status.PENDING "
			+ "and (e.lockedUntil is null or e.lockedUntil < :now)")
	int claim(@Param("id") String id, @Param("node") String node, @Param("now") Instant now,
			@Param("until") Instant until);

	long countByStatus(OutboxEvent.Status status);

	Optional<OutboxEvent> findFirstByStatusOrderByOccurredAtAsc(OutboxEvent.Status status);

	List<OutboxEvent> findByStatusOrderByOccurredAtDesc(OutboxEvent.Status status, Pageable page);

	@Modifying
	@Query("delete from OutboxEvent e where e.status = oneday.events.OutboxEvent.Status.DISPATCHED "
			+ "and e.dispatchedAt < :before")
	int deleteDispatchedBefore(@Param("before") Instant before);

	/** Erasure: every event that mentions the account, whatever its state. */
	@Modifying
	@Query("delete from OutboxEvent e where e.userIds like concat('%', :userId, '%')")
	int deleteMentioning(@Param("userId") String userId);
}
