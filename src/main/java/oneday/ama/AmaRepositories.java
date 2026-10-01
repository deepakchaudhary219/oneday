package oneday.ama;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AmaRepository extends JpaRepository<Ama, String> {

	@Query("select a from Ama a where a.cancelled = false and a.endsAt > :now and a.startsAt < :horizon order by a.startsAt")
	List<Ama> findCurrent(@Param("now") Instant now, @Param("horizon") Instant horizon);

	List<Ama> findByHostId(String hostId);

	List<Ama> findByEndsAtBefore(Instant cutoff);
}

interface AmaQuestionRepository extends JpaRepository<AmaQuestion, String> {

	List<AmaQuestion> findByAmaId(String amaId);

	long countByAmaIdAndAskerId(String amaId, String askerId);

	List<AmaQuestion> findByAskerId(String askerId);

	@Modifying
	@Query("delete from AmaQuestion q where q.amaId in :amaIds")
	void deleteByAmas(@Param("amaIds") Collection<String> amaIds);

	@Modifying
	@Query("delete from AmaQuestion q where q.askerId = :askerId")
	void deleteByAsker(@Param("askerId") String askerId);
}

interface AmaVoteRepository extends JpaRepository<AmaVote, AmaVote.Key> {

	@Query("select v.key.questionId, count(v) from AmaVote v where v.key.questionId in :questionIds group by v.key.questionId")
	List<Object[]> countFor(@Param("questionIds") Collection<String> questionIds);

	@Query("select v.key.questionId from AmaVote v where v.key.voterId = :voterId and v.key.questionId in :questionIds")
	List<String> votedBy(@Param("voterId") String voterId, @Param("questionIds") Collection<String> questionIds);

	@Modifying
	@Query("delete from AmaVote v where v.key.questionId in :questionIds")
	void deleteForQuestions(@Param("questionIds") Collection<String> questionIds);

	@Modifying
	@Query("delete from AmaVote v where v.key.voterId = :voterId")
	void deleteByVoter(@Param("voterId") String voterId);
}
