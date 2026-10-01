package oneday.events;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Inbox row: consumer {@code handler} has handled event {@code eventId}. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

	@EmbeddedId
	private Key key;

	@Column(nullable = false)
	private Instant processedAt;

	protected ProcessedEvent() {
	}

	ProcessedEvent(String handler, String eventId, Instant now) {
		this.key = new Key(handler, eventId);
		this.processedAt = now;
	}

	@Embeddable
	public record Key(@Column(name = "handler") String handler, @Column(name = "event_id") String eventId)
			implements Serializable {
	}
}
