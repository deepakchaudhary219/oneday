package oneday.connections;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A relationship between two people, stored once with {@code userA < userB}. Spark flags are private per
 * side: only their conjunction (a Mutual Spark) is ever shown to anyone.
 */
@Entity
@Table(name = "connections")
public class Connection {

	public enum State {
		ACTIVE, ENDED
	}

	@Id
	private String id;

	@Column(name = "user_a", nullable = false)
	private String userA;

	@Column(name = "user_b", nullable = false)
	private String userB;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConnectionOrigin origin;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private State state;

	@Column(name = "spark_a", nullable = false)
	private boolean sparkA;

	@Column(name = "spark_b", nullable = false)
	private boolean sparkB;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant endedAt;

	protected Connection() {
	}

	Connection(String first, String second, ConnectionOrigin origin, Instant now) {
		this.id = Ids.newId();
		this.userA = first.compareTo(second) < 0 ? first : second;
		this.userB = first.compareTo(second) < 0 ? second : first;
		this.origin = origin;
		this.state = State.ACTIVE;
		this.createdAt = now;
	}

	public boolean involves(String userId) {
		return userA.equals(userId) || userB.equals(userId);
	}

	public String otherThan(String userId) {
		return userA.equals(userId) ? userB : userA;
	}

	public boolean isActive() {
		return state == State.ACTIVE;
	}

	public boolean isMutualSpark() {
		return sparkA && sparkB;
	}

	public boolean hasSparked(String userId) {
		return userA.equals(userId) ? sparkA : sparkB;
	}

	void setSpark(String userId, boolean value) {
		if (userA.equals(userId)) {
			sparkA = value;
		}
		else {
			sparkB = value;
		}
	}

	void end(Instant now) {
		state = State.ENDED;
		endedAt = now;
		sparkA = false;
		sparkB = false;
	}

	void reactivate(ConnectionOrigin origin, Instant now) {
		this.state = State.ACTIVE;
		this.origin = origin;
		this.endedAt = null;
		this.createdAt = now;
	}

	public String getId() {
		return id;
	}

	public String getUserA() {
		return userA;
	}

	public String getUserB() {
		return userB;
	}

	public ConnectionOrigin getOrigin() {
		return origin;
	}

	public State getState() {
		return state;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}
}
