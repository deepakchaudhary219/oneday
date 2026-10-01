package oneday.plans;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlanRepository extends JpaRepository<Plan, String> {

	@Query("select p from Plan p where p.status = oneday.plans.Plan.Status.OPEN and p.endsAt > :now "
			+ "and p.lat between :minLat and :maxLat and p.lon between :minLon and :maxLon order by p.startsAt")
	List<Plan> findOpenInBox(@Param("now") Instant now, @Param("minLat") double minLat,
			@Param("maxLat") double maxLat, @Param("minLon") double minLon, @Param("maxLon") double maxLon,
			Pageable page);

	long countByHostIdAndStatusAndEndsAtAfter(String hostId, Plan.Status status, Instant now);

	List<Plan> findByStatusAndEndsAtLessThanEqual(Plan.Status status, Instant now);

	List<Plan> findByEndsAtBefore(Instant before);

	List<Plan> findByHostId(String hostId);

	@Query("select p from Plan p, PlanMember m where m.key.planId = p.id and m.key.userId = :userId "
			+ "and m.status in :statuses and p.endsAt > :since order by p.startsAt")
	List<Plan> findForMember(@Param("userId") String userId, @Param("statuses") Collection<PlanMember.Status> statuses,
			@Param("since") Instant since);
}

interface PlanMemberRepository extends JpaRepository<PlanMember, PlanMember.Key> {

	List<PlanMember> findByKeyPlanId(String planId);

	List<PlanMember> findByKeyPlanIdAndStatus(String planId, PlanMember.Status status);

	long countByKeyPlanIdAndStatusIn(String planId, Collection<PlanMember.Status> statuses);

	long countByKeyUserIdAndStatusAndCreatedAtAfter(String userId, PlanMember.Status status, Instant since);

	List<PlanMember> findByKeyUserId(String userId);

	@Modifying
	@Query("delete from PlanMember m where m.key.planId in :planIds")
	void deleteByPlanIds(@Param("planIds") Collection<String> planIds);

	@Modifying
	@Query("delete from PlanMember m where m.key.userId = :userId")
	void deleteByUserId(@Param("userId") String userId);
}

interface PlanMessageRepository extends JpaRepository<PlanMessage, String> {

	List<PlanMessage> findByPlanIdAndCreatedAtBeforeOrderByCreatedAtDesc(String planId, Instant before, Pageable page);

	@Modifying
	@Query("delete from PlanMessage m where m.planId in :planIds")
	void deleteByPlanIds(@Param("planIds") Collection<String> planIds);

	@Modifying
	@Query("delete from PlanMessage m where m.senderId = :userId")
	void deleteBySenderId(@Param("userId") String userId);
}
