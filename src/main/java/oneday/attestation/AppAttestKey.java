package oneday.attestation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A device key whose Apple attestation was verified. The public key is X.509 SubjectPublicKeyInfo DER. */
@Entity
@Table(name = "app_attest_keys")
class AppAttestKey {

	@Id
	private String keyId;

	@Column(nullable = false)
	private byte[] publicKey;

	@Column(nullable = false)
	private long signCount;

	@Column(nullable = false)
	private String environment;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant lastUsedAt;

	protected AppAttestKey() {
	}

	AppAttestKey(String keyId, byte[] publicKey, String environment, Instant now) {
		this.keyId = keyId;
		this.publicKey = publicKey;
		this.signCount = 0;
		this.environment = environment;
		this.createdAt = now;
	}

	String getKeyId() {
		return keyId;
	}

	byte[] getPublicKey() {
		return publicKey;
	}

	long getSignCount() {
		return signCount;
	}
}
