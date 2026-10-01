package oneday.grievance;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GrievanceRepository extends JpaRepository<Grievance, String> {

	List<Grievance> findByUserIdOrderByCreatedAtDesc(String userId);

	/** The Grievance Officer's queue: the nearest deadline first. */
	List<Grievance> findByStatusOrderByResolveByAsc(Grievance.Status status);

	boolean existsByReference(String reference);

	long countByStatusAndResolveByBefore(Grievance.Status status, Instant before);

	void deleteByUserId(String userId);
}
