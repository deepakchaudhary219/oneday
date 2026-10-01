package oneday.capsules;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, String> {

	@Query("select c from TimeCapsule c where c.senderId = :userId and c.status <> oneday.capsules.TimeCapsule.Status.CANCELLED order by c.opensAt")
	List<TimeCapsule> findSentBy(@Param("userId") String userId);

	@Query("select c from TimeCapsule c where c.recipientId = :userId and c.senderId <> :userId "
			+ "and c.status <> oneday.capsules.TimeCapsule.Status.CANCELLED order by c.opensAt desc")
	List<TimeCapsule> findIncomingFor(@Param("userId") String userId);

	long countBySenderIdAndStatus(String senderId, TimeCapsule.Status status);

	@Query("select c from TimeCapsule c where c.status = oneday.capsules.TimeCapsule.Status.SEALED and c.opensAt <= :now order by c.opensAt")
	List<TimeCapsule> findDue(@Param("now") Instant now, Pageable page);

	@Query("select c from TimeCapsule c where c.status = oneday.capsules.TimeCapsule.Status.SEALED and "
			+ "((c.senderId = :a and c.recipientId = :b) or (c.senderId = :b and c.recipientId = :a))")
	List<TimeCapsule> findSealedBetween(@Param("a") String a, @Param("b") String b);

	@Query("select c from TimeCapsule c where c.senderId = :userId or c.recipientId = :userId")
	List<TimeCapsule> findInvolving(@Param("userId") String userId);

	@Modifying
	@Query("delete from TimeCapsule c where c.senderId = :userId or c.recipientId = :userId")
	void deleteInvolving(@Param("userId") String userId);
}
