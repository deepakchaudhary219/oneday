package oneday.threads;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "thread_posts")
public class ThreadPost {

	public enum Kind {
		TEXT, PHOTO, VIDEO
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String threadId;

	@Column(nullable = false)
	private String authorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Kind kind;

	private String caption;

	private String mediaRef;

	private String durableMediaRef;

	private String toneFlag;

	@Column(nullable = false)
	private Instant createdAt;

	protected ThreadPost() {
	}

	ThreadPost(String threadId, String authorId, Kind kind, String caption, String mediaRef, String toneFlag,
			Instant now) {
		this.id = Ids.newId();
		this.threadId = threadId;
		this.authorId = authorId;
		this.kind = kind;
		this.caption = caption;
		this.mediaRef = mediaRef;
		this.toneFlag = toneFlag;
		this.createdAt = now;
	}

	void preserved(String durableRef) {
		this.durableMediaRef = durableRef;
	}

	String servedMediaRef() {
		return durableMediaRef != null ? durableMediaRef : mediaRef;
	}

	String getId() {
		return id;
	}

	String getThreadId() {
		return threadId;
	}

	String getAuthorId() {
		return authorId;
	}

	Kind getKind() {
		return kind;
	}

	String getCaption() {
		return caption;
	}

	String getMediaRef() {
		return mediaRef;
	}

	String getDurableMediaRef() {
		return durableMediaRef;
	}

	String getToneFlag() {
		return toneFlag;
	}

	Instant getCreatedAt() {
		return createdAt;
	}
}
