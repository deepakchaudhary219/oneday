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

	List<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(String conversationId, Instant before,
			Pageable page);

	long countByConversationIdAndSenderIdAndCreatedAtAfter(String conversationId, String senderId, Instant since);

	long countByConversationIdAndCreatedAtAfter(String conversationId, Instant since);

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

	@Modifying
	@Query("delete from Message m where m.conversationId in :conversationIds")
	void deleteByConversationIds(@Param("conversationIds") Collection<String> conversationIds);
}
