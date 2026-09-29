package oneday.identity;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A one-time code. Neither the phone number nor the code is stored in the clear. */
@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

	@Id
	private String id;

	@Column(nullable = false)
	private String phoneHash;

	@Column(nullable = false)
	private String codeHash;

	private int attempts;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant consumedAt;

	protected OtpChallenge() {
	}

	OtpChallenge(String phoneHash, String codeHash, Instant now, Instant expiresAt) {
		this.id = Ids.newId();
		this.phoneHash = phoneHash;
		this.codeHash = codeHash;
		this.createdAt = now;
		this.expiresAt = expiresAt;
	}

	boolean isUsable(Instant now, int maxAttempts) {
		return consumedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
	}

	void recordFailedAttempt() {
		attempts++;
	}

	void consume(Instant now) {
		consumedAt = now;
	}

	public String getId() {
		return id;
	}

	String getPhoneHash() {
		return phoneHash;
	}

	String getCodeHash() {
		return codeHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
