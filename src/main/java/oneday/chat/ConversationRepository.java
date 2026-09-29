package oneday.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

	Optional<Conversation> findByConnectionId(String connectionId);

	List<Conversation> findByConnectionIdIn(Collection<String> connectionIds);
}
