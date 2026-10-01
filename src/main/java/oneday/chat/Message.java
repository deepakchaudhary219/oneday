package oneday.chat;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

	@Id
	private String id;

	@Column(nullable = false)
	private String conversationId;

	@Column(nullable = false)
	private String senderId;

	@Column(nullable = false, length = 2000)
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	/** The Empathy Mirror tone of a message sent anyway after the reflection, or null. */
	private String toneFlag;

	protected Message() {
	}

	Message(String conversationId, String senderId, String body, Instant now) {
		this.id = Ids.newId();
		this.conversationId = conversationId;
		this.senderId = senderId;
		this.body = body;
		this.createdAt = now;
	}

	public String getId() {
		return id;
	}

	public String getConversationId() {
		return conversationId;
	}

	public String getSenderId() {
		return senderId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getToneFlag() {
		return toneFlag;
	}

	void flagTone(String tone) {
		this.toneFlag = tone;
	}
}
