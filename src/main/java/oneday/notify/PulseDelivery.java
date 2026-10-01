package oneday.notify;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/**
 * Claim row: at most one Local Pulse per user per local day, whichever replica gets there first. It is
 * {@link Persistable} so that saving a new claim is a real INSERT that collides on the primary key; with an
 * assigned id, Spring Data would otherwise merge (upsert) and every replica would "win".
 */
@Entity
@Table(name = "pulse_deliveries")
public class PulseDelivery implements Persistable<PulseDelivery.Key> {

	@EmbeddedId
	private Key key;

	@Transient
	private boolean isNew = true;

	private boolean sent;

	@Column(nullable = false)
	private Instant createdAt;

	protected PulseDelivery() {
	}

	PulseDelivery(String userId, LocalDate localDate, Instant now) {
		this.key = new Key(userId, localDate);
		this.createdAt = now;
	}

	@Override
	public Key getId() {
		return key;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	void markSent() {
		this.sent = true;
	}

	public boolean isSent() {
		return sent;
	}

	@Embeddable
	public record Key(@Column(name = "user_id") String userId, @Column(name = "local_date") LocalDate localDate)
			implements Serializable {
	}
}
