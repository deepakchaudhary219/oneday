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

	/** End-to-end encrypted: the body is empty here and lives only on the devices. */
	@Column(nullable = false)
	private boolean encrypted;

	/** HMAC-SHA256(franking key, plaintext), committed by the sender; verifies a recipient's report. */
	private byte[] frankingCommitment;

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

	static Message encrypted(String conversationId, String senderId, byte[] frankingCommitment, Instant now) {
		Message message = new Message(conversationId, senderId, "", now);
		message.encrypted = true;
		message.frankingCommitment = frankingCommitment;
		return message;
	}

	public boolean isEncrypted() {
		return encrypted;
	}

	byte[] getFrankingCommitment() {
		return frankingCommitment;
	}

	public String getToneFlag() {
		return toneFlag;
	}

	void flagTone(String tone) {
		this.toneFlag = tone;
	}
}
