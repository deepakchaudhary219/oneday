package oneday.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One signed-in device. Ending it cuts off its access token at once and its refresh token for good. */
@Entity
@Table(name = "sessions")
public class Session {

	public enum EndReason {
		/** Signed out on this device. */
		SIGNED_OUT,
		/** Signed out everywhere, or this device was signed out from another one. */
		SIGNED_OUT_ELSEWHERE,
		/** An old refresh token came back after rotation: someone else may hold a copy. */
		REUSE_DETECTED,
		/** Too many devices; the least recently used was signed out. */
		DEVICE_LIMIT,
		/** Trust & Safety suspended the account. */
		SUSPENDED,
		/** The holder asked for erasure. */
		ERASURE
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false)
	private String refreshHash;

	private String previousHash;

	private Instant rotatedAt;

	private String device;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant lastUsedAt;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant endedAt;

	@Enumerated(EnumType.STRING)
	private EndReason endReason;

	protected Session() {
	}

	Session(String userId, String refreshHash, String device, Instant now, Instant expiresAt) {
		this.id = Ids.newId();
		this.userId = userId;
		this.refreshHash = refreshHash;
		this.device = device;
		this.createdAt = now;
		this.lastUsedAt = now;
		this.expiresAt = expiresAt;
	}

	boolean isActive(Instant now) {
		return endedAt == null && now.isBefore(expiresAt);
	}

	boolean isCurrent(String presentedHash) {
		return same(refreshHash, presentedHash);
	}

	/** The secret just replaced, presented again within the grace period: a client retrying a lost response. */
	boolean isRetryOfLastRotation(String presentedHash, Instant now, Duration grace) {
		return previousHash != null && rotatedAt != null && now.isBefore(rotatedAt.plus(grace))
				&& same(previousHash, presentedHash);
	}

	/**
	 * Replaces the refresh secret. A retry keeps the secret the client actually holds as the previous one, so
	 * the secret whose response was lost can never be used.
	 */
	void rotate(String newHash, boolean retry, Instant now, Instant expiresAt) {
		if (!retry) {
			this.previousHash = this.refreshHash;
			this.rotatedAt = now;
		}
		this.refreshHash = newHash;
		this.lastUsedAt = now;
		this.expiresAt = expiresAt;
	}

	void end(EndReason reason, Instant now) {
		if (endedAt == null) {
			this.endedAt = now;
			this.endReason = reason;
		}
	}

	private static boolean same(String a, String b) {
		return a != null && b != null
				&& MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
	}

	public String getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public String getDevice() {
		return device;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public EndReason getEndReason() {
		return endReason;
	}
}
