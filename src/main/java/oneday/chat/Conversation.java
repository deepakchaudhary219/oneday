package oneday.chat;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One conversation per connection, seeded with the context that brought the two people together. */
@Entity
@Table(name = "conversations")
public class Conversation {

	@Id
	private String id;

	@Column(nullable = false, unique = true)
	private String connectionId;

	private String seedContext;

	@Column(nullable = false)
	private Instant createdAt;

	protected Conversation() {
	}

	Conversation(String connectionId, String seedContext, Instant now) {
		this.id = Ids.newId();
		this.connectionId = connectionId;
		this.seedContext = seedContext;
		this.createdAt = now;
	}

	public String getId() {
		return id;
	}

	public String getConnectionId() {
		return connectionId;
	}

	public String getSeedContext() {
		return seedContext;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
