package oneday.ledger;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One real-world outcome in one person's private ledger. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

	public enum Kind {
		/** A Mutual Reveal: a new person in your life. */
		NEW_CONNECTION,
		/** A new connection with someone from your home region (Roots). */
		ROOTS_CONNECTION,
		MUTUAL_SPARK,
		/** A Date Mode plan that actually happened. */
		DATE_COMPLETED,
		/** Couple Mode switched on: the app worked, and you need it less. */
		COUPLE_FORMED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Kind kind;

	@Column(nullable = false)
	private Instant occurredAt;

	@Column(nullable = false)
	private String sourceEventId;

	protected LedgerEntry() {
	}

	LedgerEntry(String userId, Kind kind, Instant occurredAt, String sourceEventId) {
		this.id = Ids.newId();
		this.userId = userId;
		this.kind = kind;
		this.occurredAt = occurredAt;
		this.sourceEventId = sourceEventId;
	}

	public Kind getKind() {
		return kind;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
