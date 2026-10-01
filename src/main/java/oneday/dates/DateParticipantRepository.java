package oneday.dates;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DateParticipantRepository extends JpaRepository<DateParticipant, DateParticipant.Key> {

	List<DateParticipant> findByKeyDateId(String dateId);

	Optional<DateParticipant> findByShareTokenHash(String shareTokenHash);

	/** Check-ins that are due and not yet prompted, on confirmed plans. */
	@Query("select d from DateParticipant d, DatePlan p where p.id = d.key.dateId "
			+ "and p.status = oneday.dates.DatePlan.Status.CONFIRMED and d.checkInDueAt <= :now "
			+ "and d.checkInPromptedAt is null and d.checkedInAt is null and d.homeSafeAt is null")
	List<DateParticipant> findCheckInsDue(@Param("now") Instant now);

	/** Prompts nobody answered within the grace period, not yet escalated. */
	@Query("select d from DateParticipant d, DatePlan p where p.id = d.key.dateId "
			+ "and p.status in :statuses and d.checkInPromptedAt <= :promptedBefore "
			+ "and d.checkedInAt is null and d.escalatedAt is null and d.homeSafeAt is null "
			+ "and d.contactPhone is not null")
	List<DateParticipant> findCheckInsMissed(@Param("promptedBefore") Instant promptedBefore,
			@Param("statuses") Collection<DatePlan.Status> statuses);

	@Query("select d from DateParticipant d where d.escalatedAt is not null and d.escalationResolvedAt is null "
			+ "order by d.escalatedAt asc")
	List<DateParticipant> findOpenEscalations();

	@Modifying
	@Query("delete from DateParticipant d where d.key.dateId in :dateIds")
	void deleteByDateIds(@Param("dateIds") Collection<String> dateIds);
}
