package oneday.consent;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConsentRecordRepository extends JpaRepository<ConsentRecord, String> {

	Optional<ConsentRecord> findFirstByUserIdAndPurposeOrderByCreatedAtDescIdDesc(String userId, ConsentPurpose purpose);

	List<ConsentRecord> findByUserIdOrderByCreatedAtAscIdAsc(String userId);
}
