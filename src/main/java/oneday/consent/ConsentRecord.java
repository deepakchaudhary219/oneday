package oneday.consent;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One consent event. Never updated (append-only) except to pseudonymise it on erasure. */
@Entity
@Table(name = "consent_records")
public class ConsentRecord {

	public enum Action {
		GRANTED, WITHDRAWN
	}

	/** How consent was expressed. */
	public enum Source {
		/** A clear affirmative action in the app after the notice (DPDP s.6(1)), e.g. first sharing location. */
		APP_ACTION,
		/** The privacy settings screen. */
		SETTINGS
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConsentPurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Action action;

	@Column(nullable = false)
	private String noticeVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Source source;

	@Column(nullable = false)
	private Instant createdAt;

	protected ConsentRecord() {
	}

	ConsentRecord(String userId, ConsentPurpose purpose, Action action, String noticeVersion, Source source,
			Instant now) {
		this.id = Ids.newId();
		this.userId = userId;
		this.purpose = purpose;
		this.action = action;
		this.noticeVersion = noticeVersion;
		this.source = source;
		this.createdAt = now;
	}

	void pseudonymise(String ref) {
		userId = ref;
	}

	public ConsentPurpose getPurpose() {
		return purpose;
	}

	public Action getAction() {
		return action;
	}

	public String getNoticeVersion() {
		return noticeVersion;
	}

	public Source getSource() {
		return source;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
