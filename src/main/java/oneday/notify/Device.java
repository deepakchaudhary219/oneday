package oneday.notify;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "devices")
public class Device {

	public enum Platform {
		ANDROID, IOS
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false, unique = true)
	private String pushToken;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Platform platform;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant lastSeenAt;

	protected Device() {
	}

	Device(String userId, String pushToken, Platform platform, Instant now) {
		this.id = Ids.newId();
		this.userId = userId;
		this.pushToken = pushToken;
		this.platform = platform;
		this.createdAt = now;
		this.lastSeenAt = now;
	}

	/** A shared phone signing into another account moves the token; it never notifies two accounts. */
	void assignTo(String userId, Platform platform, Instant now) {
		this.userId = userId;
		this.platform = platform;
		this.lastSeenAt = now;
	}

	public String getUserId() {
		return userId;
	}

	public String getPushToken() {
		return pushToken;
	}

	public Platform getPlatform() {
		return platform;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}
}
