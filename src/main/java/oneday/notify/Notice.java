package oneday.notify;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An in-app message from OneDay itself (safety outcomes), kept so it survives a missed push. */
@Entity
@Table(name = "notices")
public class Notice {

	public enum Kind {
		/** A report against you was upheld and you were warned. */
		WARNING,
		/** The outcome of a report you filed. */
		REPORT_UPDATE
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Kind kind;

	@Column(nullable = false)
	private String message;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant readAt;

	protected Notice() {
	}

	Notice(String userId, Kind kind, String message, Instant now) {
		this.id = Ids.newId();
		this.userId = userId;
		this.kind = kind;
		this.message = message;
		this.createdAt = now;
	}

	void markRead(Instant now) {
		if (readAt == null) {
			readAt = now;
		}
	}

	public String getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public Kind getKind() {
		return kind;
	}

	public String getMessage() {
		return message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getReadAt() {
		return readAt;
	}
}
