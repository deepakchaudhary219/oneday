package oneday.safety;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, String> {

	List<Report> findByReporterIdOrderByCreatedAtDesc(String reporterId);

	List<Report> findByStatusIn(Collection<Report.Status> statuses);

	long countByReportedId(String reportedId);

	boolean existsByReportedIdAndPriorityAndStatusIn(String reportedId, ReportCategory.Priority priority,
			Collection<Report.Status> statuses);

	Optional<Report> findFirstByReportedIdAndResolutionAndResolvedAtAfterOrderByResolvedAtDesc(String reportedId,
			Report.Resolution resolution, Instant after);

	@Modifying
	@Query("update Report r set r.reporterId = null where r.reporterId = :userId")
	void detachReporter(@Param("userId") String userId);
}
