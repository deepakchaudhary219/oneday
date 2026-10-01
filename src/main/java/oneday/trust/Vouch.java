package oneday.trust;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** "I know this person and they are who they say they are." */
@Entity
@Table(name = "vouches")
public class Vouch {

	@EmbeddedId
	private Key key;

	@Column(nullable = false)
	private Instant createdAt;

	protected Vouch() {
	}

	Vouch(String voucherId, String voucheeId, Instant now) {
		this.key = new Key(voucherId, voucheeId);
		this.createdAt = now;
	}

	public String getVoucherId() {
		return key.voucherId();
	}

	public String getVoucheeId() {
		return key.voucheeId();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Embeddable
	public record Key(@Column(name = "voucher_id") String voucherId, @Column(name = "vouchee_id") String voucheeId)
			implements Serializable {
	}
}
