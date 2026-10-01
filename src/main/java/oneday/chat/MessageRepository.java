package oneday.chat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, String> {

	/** The newest message of each conversation, in one query (chat list previews). */
	@Query("select m from Message m where m.conversationId in :ids and m.createdAt = "
			+ "(select max(m2.createdAt) from Message m2 where m2.conversationId = m.conversationId)")
	List<Message> findLatestIn(@Param("ids") Collection<String> ids);

	List<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(String conversationId, Instant before,
			Pageable page);

	long countByConversationIdAndSenderIdAndCreatedAtAfter(String conversationId, String senderId, Instant since);

	long countByConversationIdAndCreatedAtAfter(String conversationId, Instant since);

	boolean existsByConversationIdAndSenderId(String conversationId, String senderId);

	List<Message> findBySenderIdOrderByCreatedAtAsc(String senderId);

	/**
	 * Conversations among {@code conversationIds} where, in the period, both people wrote and at least
	 * {@code minTotal} messages were exchanged: "a conversation that went somewhere".
	 */
	@Query("select m.conversationId from Message m where m.conversationId in :conversationIds "
			+ "and m.createdAt >= :from and m.createdAt < :to group by m.conversationId "
			+ "having count(distinct m.senderId) = 2 and count(m) >= :minTotal")
	List<String> findActiveConversations(@Param("conversationIds") Collection<String> conversationIds,
			@Param("from") Instant from, @Param("to") Instant to, @Param("minTotal") long minTotal);

	@Query("select m.senderId, m.createdAt from Message m where m.conversationId = :conversationId "
			+ "and m.createdAt >= :since")
	List<Object[]> findSendersSince(@Param("conversationId") String conversationId, @Param("since") Instant since,
			Pageable page);

	/** People who wrote in a conversation where the other person also wrote, within [from, to). */
	@Query("select distinct m.senderId from Message m where m.createdAt >= :from and m.createdAt < :to and exists "
			+ "(select o.id from Message o where o.conversationId = m.conversationId and o.senderId <> m.senderId "
			+ "and o.createdAt >= :from and o.createdAt < :to)")
	List<String> findTwoWayWriters(@Param("from") Instant from, @Param("to") Instant to);

	@Modifying
	@Query("delete from Message m where m.conversationId in :conversationIds")
	void deleteByConversationIds(@Param("conversationIds") Collection<String> conversationIds);
}
