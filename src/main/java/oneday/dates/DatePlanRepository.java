package oneday.dates;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatePlanRepository extends JpaRepository<DatePlan, String> {

	boolean existsByConnectionIdAndStatusIn(String connectionId, Collection<DatePlan.Status> statuses);

	@Query("select p from DatePlan p where (p.proposerId = :userId or p.partnerId = :userId) "
			+ "and (p.status in :open or p.closedAt > :closedSince) order by p.startsAt asc")
	List<DatePlan> findForUser(@Param("userId") String userId, @Param("open") Collection<DatePlan.Status> open,
			@Param("closedSince") Instant closedSince);

	@Query("select p from DatePlan p where p.proposerId = :userId or p.partnerId = :userId")
	List<DatePlan> findAllInvolving(@Param("userId") String userId);

	@Query("select p from DatePlan p where p.status in :open and ((p.proposerId = :a and p.partnerId = :b) "
			+ "or (p.proposerId = :b and p.partnerId = :a))")
	List<DatePlan> findOpenBetween(@Param("a") String a, @Param("b") String b,
			@Param("open") Collection<DatePlan.Status> open);

	List<DatePlan> findByStatusAndEndsAtLessThanEqual(DatePlan.Status status, Instant now);

	List<DatePlan> findByStatusAndStartsAtLessThanEqual(DatePlan.Status status, Instant now);

	List<DatePlan> findByStatusInAndClosedAtLessThanEqual(Collection<DatePlan.Status> statuses, Instant before);
}
