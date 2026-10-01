package oneday.plus;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscriptions")
public class Subscription {

	public enum Status {
		/** Created; waiting for the first mandate/payment. */
		CREATED,
		/** Paid up to {@code currentEnd}. */
		ACTIVE,
		/** Payment retries failed; Plus ends after the grace period. */
		HALTED,
		CANCELLED,
		COMPLETED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false)
	private String provider;

	@Column(nullable = false)
	private String providerSubscriptionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	private Instant currentEnd;

	@Column(nullable = false)
	private boolean cancelAtCycleEnd;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Subscription() {
	}

	Subscription(String userId, String provider, String providerSubscriptionId, Instant now) {
		this.id = Ids.newId();
		this.userId = userId;
		this.provider = provider;
		this.providerSubscriptionId = providerSubscriptionId;
		this.status = Status.CREATED;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void update(Status status, Instant currentEnd, Instant now) {
		this.status = status;
		if (currentEnd != null) {
			this.currentEnd = currentEnd;
		}
		this.updatedAt = now;
	}

	void cancelAtCycleEnd(Instant now) {
		cancelAtCycleEnd = true;
		updatedAt = now;
	}

	/** Erasure: the record stays (tax law) but no longer names the person. */
	void detach(Instant now) {
		userId = "erased";
		updatedAt = now;
	}

	public String getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public String getProviderSubscriptionId() {
		return providerSubscriptionId;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getCurrentEnd() {
		return currentEnd;
	}

	public boolean isCancelAtCycleEnd() {
		return cancelAtCycleEnd;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
