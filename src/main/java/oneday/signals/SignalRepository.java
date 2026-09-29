package oneday.signals;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalRepository extends JpaRepository<Signal, String> {

	boolean existsBySenderIdAndMomentId(String senderId, String momentId);

	long countBySenderIdAndCreatedAtAfter(String senderId, Instant since);

	List<Signal> findByRecipientIdAndStatusAndWindowExpiresAtAfter(String recipientId, Signal.Status status,
			Instant now);

	long countByRecipientIdAndStatusAndWindowExpiresAtAfter(String recipientId, Signal.Status status, Instant now);

	List<Signal> findBySenderIdOrderByCreatedAtDesc(String senderId, Pageable page);

	List<Signal> findBySenderIdOrderByCreatedAtDesc(String senderId);

	List<Signal> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

	boolean existsBySenderIdAndRecipientIdAndCreatedAtAfter(String senderId, String recipientId, Instant since);

	@Query("select distinct s.recipientId from Signal s where s.senderId = :senderId and s.createdAt > :since")
	List<String> findRecipientsSignalledSince(@Param("senderId") String senderId, @Param("since") Instant since);

	@Modifying
	@Query("update Signal s set s.status = oneday.signals.Signal.Status.ARCHIVED, s.resolvedAt = :now "
			+ "where s.status = oneday.signals.Signal.Status.PENDING and s.windowExpiresAt <= :now")
	int archiveExpired(@Param("now") Instant now);

	@Modifying
	@Query("update Signal s set s.status = oneday.signals.Signal.Status.ARCHIVED, s.resolvedAt = :now "
			+ "where s.status = oneday.signals.Signal.Status.PENDING and "
			+ "((s.senderId = :a and s.recipientId = :b) or (s.senderId = :b and s.recipientId = :a))")
	int archivePendingBetween(@Param("a") String a, @Param("b") String b, @Param("now") Instant now);

	@Modifying
	@Query("delete from Signal s where s.senderId = :userId or s.recipientId = :userId")
	void deleteInvolving(@Param("userId") String userId);
}
