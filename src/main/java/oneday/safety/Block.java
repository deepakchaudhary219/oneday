package oneday.safety;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blocks")
public class Block {

	@Id
	private String id;

	@Column(nullable = false)
	private String blockerId;

	@Column(nullable = false)
	private String blockedId;

	@Column(nullable = false)
	private Instant createdAt;

	protected Block() {
	}

	Block(String blockerId, String blockedId, Instant now) {
		this.id = Ids.newId();
		this.blockerId = blockerId;
		this.blockedId = blockedId;
		this.createdAt = now;
	}

	public String getBlockerId() {
		return blockerId;
	}

	public String getBlockedId() {
		return blockedId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
