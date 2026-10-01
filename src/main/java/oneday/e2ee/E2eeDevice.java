package oneday.e2ee;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An app install's public keys. Identity keys never change: a new identity is a new device. */
@Entity
@Table(name = "e2ee_devices")
class E2eeDevice {

	@Embeddable
	record Key(@Column(name = "user_id") String userId, @Column(name = "device_id") int deviceId) {
	}

	@EmbeddedId
	private Key key;

	@Column(nullable = false)
	private String sessionId;

	@Column(nullable = false)
	private int registrationId;

	@Column(nullable = false)
	private byte[] identityKey;

	@Column(name = "signed_prekey_id", nullable = false)
	private int signedPreKeyId;

	@Column(name = "signed_prekey", nullable = false)
	private byte[] signedPreKey;

	@Column(name = "signed_prekey_signature", nullable = false)
	private byte[] signedPreKeySignature;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected E2eeDevice() {
	}

	E2eeDevice(Key key, String sessionId, int registrationId, byte[] identityKey, Instant now) {
		this.key = key;
		this.sessionId = sessionId;
		this.registrationId = registrationId;
		this.identityKey = identityKey;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void rotateSignedPreKey(int keyId, byte[] publicKey, byte[] signature, Instant now) {
		this.signedPreKeyId = keyId;
		this.signedPreKey = publicKey;
		this.signedPreKeySignature = signature;
		this.updatedAt = now;
	}

	Key getKey() {
		return key;
	}

	int deviceId() {
		return key.deviceId();
	}

	String getSessionId() {
		return sessionId;
	}

	int getRegistrationId() {
		return registrationId;
	}

	byte[] getIdentityKey() {
		return identityKey;
	}

	int getSignedPreKeyId() {
		return signedPreKeyId;
	}

	byte[] getSignedPreKey() {
		return signedPreKey;
	}

	byte[] getSignedPreKeySignature() {
		return signedPreKeySignature;
	}

	Instant getCreatedAt() {
		return createdAt;
	}
}
