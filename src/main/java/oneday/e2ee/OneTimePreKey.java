package oneday.e2ee;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "e2ee_one_time_prekeys")
class OneTimePreKey {

	@Embeddable
	record Key(@Column(name = "user_id") String userId, @Column(name = "device_id") int deviceId,
			@Column(name = "key_id") int keyId) {
	}

	@EmbeddedId
	private Key key;

	@Column(nullable = false)
	private byte[] publicKey;

	protected OneTimePreKey() {
	}

	OneTimePreKey(Key key, byte[] publicKey) {
		this.key = key;
		this.publicKey = publicKey;
	}

	Key getKey() {
		return key;
	}

	byte[] getPublicKey() {
		return publicKey;
	}
}
