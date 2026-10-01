package oneday.ledger;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerRepository extends JpaRepository<LedgerEntry, String> {

	boolean existsByUserIdAndKindAndSourceEventId(String userId, LedgerEntry.Kind kind, String sourceEventId);

	List<LedgerEntry> findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(String userId, Instant from,
			Instant to);

	List<LedgerEntry> findByUserIdOrderByOccurredAtAsc(String userId);

	@Modifying
	@Query("delete from LedgerEntry e where e.userId = :userId")
	void deleteByUserId(@Param("userId") String userId);
}
