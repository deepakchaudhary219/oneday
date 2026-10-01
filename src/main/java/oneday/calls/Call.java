package oneday.calls;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One call. Each person sets the layer they're comfortable with; the call runs at the lower of the two, so a
 * step up needs both and a step down needs only one.
 */
@Entity
@Table(name = "calls")
class Call {

	enum Status {
		RINGING, ACTIVE, ENDED;

		boolean isOpen() {
			return this != ENDED;
		}
	}

	enum EndReason {
		HUNG_UP, DECLINED, MISSED, TIMED_OUT, BLOCKED
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String connectionId;

	@Column(nullable = false)
	private String callerId;

	@Column(nullable = false)
	private String calleeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Layer callerWants;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Layer calleeWants;

	@Enumerated(EnumType.STRING)
	private EndReason endReason;

	@Column(nullable = false)
	private Instant startedAt;

	private Instant answeredAt;

	private Instant endedAt;

	protected Call() {
	}

	Call(String connectionId, String callerId, String calleeId, Instant now) {
		this.id = Ids.newId();
		this.connectionId = connectionId;
		this.callerId = callerId;
		this.calleeId = calleeId;
		this.status = Status.RINGING;
		this.callerWants = Layer.VOICE;
		this.calleeWants = Layer.VOICE;
		this.startedAt = now;
	}

	boolean involves(String userId) {
		return callerId.equals(userId) || calleeId.equals(userId);
	}

	String otherThan(String userId) {
		return callerId.equals(userId) ? calleeId : callerId;
	}

	Layer layer() {
		return callerWants.compareTo(calleeWants) <= 0 ? callerWants : calleeWants;
	}

	Layer wantsOf(String userId) {
		return callerId.equals(userId) ? callerWants : calleeWants;
	}

	void setWants(String userId, Layer wants) {
		if (callerId.equals(userId)) {
			callerWants = wants;
		}
		else {
			calleeWants = wants;
		}
	}

	void answer(Instant now) {
		status = Status.ACTIVE;
		answeredAt = now;
	}

	void end(EndReason reason, Instant now) {
		status = Status.ENDED;
		endReason = reason;
		endedAt = now;
		callerWants = Layer.VOICE;
		calleeWants = Layer.VOICE;
	}

	String getId() {
		return id;
	}

	String getConnectionId() {
		return connectionId;
	}

	String getCallerId() {
		return callerId;
	}

	String getCalleeId() {
		return calleeId;
	}

	Status getStatus() {
		return status;
	}

	EndReason getEndReason() {
		return endReason;
	}

	Instant getStartedAt() {
		return startedAt;
	}

	Instant getAnsweredAt() {
		return answeredAt;
	}

	Instant getEndedAt() {
		return endedAt;
	}
}
