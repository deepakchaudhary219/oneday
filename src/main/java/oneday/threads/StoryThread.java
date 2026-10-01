package oneday.threads;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "threads")
class StoryThread {

	@Id
	private String id;

	@Column(nullable = false)
	private String creatorId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant endsAt;

	protected StoryThread() {
	}

	StoryThread(String creatorId, String title, Instant now, Instant endsAt) {
		this.id = Ids.newId();
		this.creatorId = creatorId;
		this.title = title;
		this.createdAt = now;
		this.endsAt = endsAt;
	}

	boolean isOpen(Instant now) {
		return endsAt.isAfter(now);
	}

	String getId() {
		return id;
	}

	String getCreatorId() {
		return creatorId;
	}

	String getTitle() {
		return title;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getEndsAt() {
		return endsAt;
	}
}
