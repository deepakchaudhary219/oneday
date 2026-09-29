package oneday.safety;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, String> {

	List<Report> findByReporterIdOrderByCreatedAtDesc(String reporterId);

	List<Report> findByPriorityAndStatusOrderByCreatedAtAsc(ReportCategory.Priority priority, Report.Status status);

	@Modifying
	@Query("update Report r set r.reporterId = null where r.reporterId = :userId")
	void detachReporter(@Param("userId") String userId);
}
