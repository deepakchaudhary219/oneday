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

	@Modifying
	@Query("delete from Message m where m.conversationId in :conversationIds")
	void deleteByConversationIds(@Param("conversationIds") Collection<String> conversationIds);
}
