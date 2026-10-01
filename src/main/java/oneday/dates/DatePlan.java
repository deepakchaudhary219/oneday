package oneday.dates;

import java.time.Duration;
import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A meet-up two Connections agreed on. The state machine:
 *
 * <pre>
 * PROPOSED ──accept──► CONFIRMED ──end / time box over──► ENDED (completed)
 *    │ ├──decline──► DECLINED        └──cancel──► CANCELLED
 *    │ └──cancel───► CANCELLED
 *    └──start passes unanswered──► EXPIRED
 * </pre>
 */
@Entity
@Table(name = "date_plans")
public class DatePlan {

	public enum Status {
		PROPOSED, CONFIRMED, ENDED, CANCELLED, DECLINED, EXPIRED;

		public boolean isOpen() {
			return this == PROPOSED || this == CONFIRMED;
		}
	}

	@Id
	private String id;

	@Column(nullable = false)
	private String connectionId;

	@Column(nullable = false)
	private String proposerId;

	@Column(nullable = false)
	private String partnerId;

	private String meetingPointId;

	@Column(nullable = false)
	private String placeName;

	@Column(nullable = false)
	private Instant startsAt;

	@Column(nullable = false)
	private Instant endsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	/** The two people actually met: the plan was confirmed and its start time passed before it closed. */
	@Column(nullable = false)
	private boolean completed;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant confirmedAt;

	private Instant closedAt;

	/** Optimistic locking: the scheduler and the two people can race on the same plan. */
	@Version
	@Column(name = "row_version")
	private long version;

	protected DatePlan() {
	}

	DatePlan(String connectionId, String proposerId, String partnerId, String meetingPointId, String placeName,
			Instant startsAt, Instant endsAt, Instant now) {
		this.id = Ids.newId();
		this.connectionId = connectionId;
		this.proposerId = proposerId;
		this.partnerId = partnerId;
		this.meetingPointId = meetingPointId;
		this.placeName = placeName;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.status = Status.PROPOSED;
		this.createdAt = now;
	}

	public boolean involves(String userId) {
		return proposerId.equals(userId) || partnerId.equals(userId);
	}

	public String otherThan(String userId) {
		return proposerId.equals(userId) ? partnerId : proposerId;
	}

	void confirm(Instant now) {
		status = Status.CONFIRMED;
		confirmedAt = now;
	}

	/** Closes the plan. Returns true when this call is what made a confirmed plan count as completed. */
	boolean close(Status to, Instant now) {
		boolean becameCompleted = status == Status.CONFIRMED && to == Status.ENDED && !now.isBefore(startsAt);
		status = to;
		closedAt = now;
		completed = completed || becameCompleted;
		return becameCompleted;
	}

	/** Exact location may flow between the two people: confirmed, and inside the time box. */
	boolean locationWindowOpen(Instant now, Duration lead) {
		return status == Status.CONFIRMED && !now.isBefore(startsAt.minus(lead)) && now.isBefore(endsAt);
	}

	/**
	 * SOS and the trusted contact's page work on a plan that was confirmed, from shortly before its start until
	 * {@code afterCare} past its end, or past the moment it was ended or cancelled. Cancelling mid-date (for
	 * instance by blocking) therefore never switches help off for the person who needs it.
	 */
	boolean inCareWindow(Instant now, Duration lead, Duration afterCare) {
		if (confirmedAt == null) {
			return false;
		}
		Instant over = closedAt != null && closedAt.isBefore(endsAt) ? closedAt : endsAt;
		return !now.isBefore(startsAt.minus(lead)) && now.isBefore(over.plus(afterCare));
	}

	public String getId() {
		return id;
	}

	public String getConnectionId() {
		return connectionId;
	}

	public String getProposerId() {
		return proposerId;
	}

	public String getPartnerId() {
		return partnerId;
	}

	public String getMeetingPointId() {
		return meetingPointId;
	}

	public String getPlaceName() {
		return placeName;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public Status getStatus() {
		return status;
	}

	public boolean isCompleted() {
		return completed;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}
}
