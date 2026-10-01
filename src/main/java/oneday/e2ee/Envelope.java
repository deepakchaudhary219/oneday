package oneday.e2ee;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One device's copy of one encrypted message. */
@Entity
@Table(name = "e2ee_envelopes")
class Envelope {

	@Id
	private String id;

	@Column(nullable = false)
	private String messageId;

	@Column(nullable = false)
	private String conversationId;

	@Column(nullable = false)
	private String senderUserId;

	@Column(nullable = false)
	private int senderDeviceId;

	@Column(nullable = false)
	private String recipientUserId;

	@Column(nullable = false)
	private int recipientDeviceId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EnvelopeType type;

	@Column(nullable = false)
	private byte[] ciphertext;

	@Column(nullable = false)
	private Instant createdAt;

	protected Envelope() {
	}

	Envelope(String messageId, String conversationId, String senderUserId, int senderDeviceId, String recipientUserId,
			int recipientDeviceId, EnvelopeType type, byte[] ciphertext, Instant now) {
		this.id = Ids.newId();
		this.messageId = messageId;
		this.conversationId = conversationId;
		this.senderUserId = senderUserId;
		this.senderDeviceId = senderDeviceId;
		this.recipientUserId = recipientUserId;
		this.recipientDeviceId = recipientDeviceId;
		this.type = type;
		this.ciphertext = ciphertext;
		this.createdAt = now;
	}

	String getId() {
		return id;
	}

	String getMessageId() {
		return messageId;
	}

	String getConversationId() {
		return conversationId;
	}

	String getSenderUserId() {
		return senderUserId;
	}

	int getSenderDeviceId() {
		return senderDeviceId;
	}

	EnvelopeType getType() {
		return type;
	}

	byte[] getCiphertext() {
		return ciphertext;
	}

	Instant getCreatedAt() {
		return createdAt;
	}
}
