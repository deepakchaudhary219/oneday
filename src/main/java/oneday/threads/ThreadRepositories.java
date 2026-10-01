package oneday.threads;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ThreadRepository extends JpaRepository<StoryThread, String> {

	List<StoryThread> findByEndsAtBefore(Instant cutoff);

	List<StoryThread> findByCreatorId(String creatorId);
}

interface ThreadMemberRepository extends JpaRepository<ThreadMember, ThreadMember.Key> {

	@Query("select m from ThreadMember m where m.key.threadId = :threadId")
	List<ThreadMember> findByThread(@Param("threadId") String threadId);

	@Query("select m from ThreadMember m where m.key.userId = :userId and m.status in :statuses")
	List<ThreadMember> findByUser(@Param("userId") String userId,
			@Param("statuses") Collection<ThreadMember.Status> statuses);

	@Modifying
	@Query("delete from ThreadMember m where m.key.threadId in :threadIds")
	void deleteByThreads(@Param("threadIds") Collection<String> threadIds);

	@Modifying
	@Query("delete from ThreadMember m where m.key.userId = :userId")
	void deleteByUser(@Param("userId") String userId);
}

interface ThreadPostRepository extends JpaRepository<ThreadPost, String> {

	@Query("select p from ThreadPost p where p.threadId = :threadId and p.createdAt < :before order by p.createdAt desc, p.id desc")
	List<ThreadPost> findPage(@Param("threadId") String threadId, @Param("before") Instant before, Pageable page);

	List<ThreadPost> findByThreadIdIn(Collection<String> threadIds);

	List<ThreadPost> findByAuthorId(String authorId);

	@Modifying
	@Query("delete from ThreadPost p where p.threadId in :threadIds")
	void deleteByThreads(@Param("threadIds") Collection<String> threadIds);

	@Modifying
	@Query("delete from ThreadPost p where p.authorId = :authorId")
	void deleteByAuthor(@Param("authorId") String authorId);
}
