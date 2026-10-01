package oneday.dates;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One person's side of a plan: their own consent to share exact location, their current position (one point,
 * overwritten, never a trail), their check-in, their trusted contact and their private debrief.
 */
@Entity
@Table(name = "date_participants")
public class DateParticipant {

	@EmbeddedId
	private Key key;

	@Column(nullable = false)
	private boolean shareLocation;

	private Double lat;

	private Double lon;

	private Instant locationAt;

	private Instant checkInDueAt;

	private Instant checkInPromptedAt;

	private Instant checkedInAt;

	private Instant escalatedAt;

	@Enumerated(EnumType.STRING)
	private EscalationReason escalationReason;

	private Instant escalationResolvedAt;

	private String escalationResolvedBy;

	private String contactName;

	private String contactPhone;

	private String shareTokenHash;

	private Instant homeSafeAt;

	private String debrief;

	private Instant debriefAt;

	private Instant purgedAt;

	protected DateParticipant() {
	}

	DateParticipant(String dateId, String userId, Instant checkInDueAt) {
		this.key = new Key(dateId, userId);
		this.checkInDueAt = checkInDueAt;
	}

	void setShareLocation(boolean enabled) {
		shareLocation = enabled;
		if (!enabled) {
			clearLocation();
		}
	}

	void moveTo(double lat, double lon, Instant now) {
		this.lat = lat;
		this.lon = lon;
		this.locationAt = now;
	}

	boolean hasFreshLocation(Instant now, Duration freshness) {
		return shareLocation && lat != null && locationAt != null && now.isBefore(locationAt.plus(freshness));
	}

	void clearLocation() {
		lat = null;
		lon = null;
		locationAt = null;
	}

	void setTrustedContact(String name, String phone, String tokenHash) {
		contactName = name;
		contactPhone = phone;
		shareTokenHash = tokenHash;
	}

	boolean hasTrustedContact() {
		return contactPhone != null;
	}

	void setCheckInDueAt(Instant due) {
		checkInDueAt = due;
		checkInPromptedAt = null;
	}

	void prompted(Instant now) {
		checkInPromptedAt = now;
	}

	void checkedIn(Instant now) {
		checkedInAt = now;
	}

	/** Returns false if an escalation is already open (the first reason stands; nothing is sent twice). */
	boolean escalate(EscalationReason reason, Instant now) {
		if (hasOpenEscalation()) {
			return false;
		}
		escalatedAt = now;
		escalationReason = reason;
		escalationResolvedAt = null;
		escalationResolvedBy = null;
		return true;
	}

	boolean hasOpenEscalation() {
		return escalatedAt != null && escalationResolvedAt == null;
	}

	void resolveEscalation(String staffId, Instant now) {
		escalationResolvedAt = now;
		escalationResolvedBy = staffId;
	}

	void homeSafe(Instant now) {
		if (homeSafeAt == null) {
			homeSafeAt = now;
		}
	}

	void debrief(Set<DebriefAnswer> answers, Instant now) {
		debrief = answers.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
		debriefAt = now;
	}

	Set<DebriefAnswer> debriefAnswers() {
		if (debrief == null || debrief.isBlank()) {
			return EnumSet.noneOf(DebriefAnswer.class);
		}
		return Arrays.stream(debrief.split(","))
			.map(DebriefAnswer::valueOf)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(DebriefAnswer.class)));
	}

	/** Drops everything sensitive once the plan is over: position, trusted contact and the share link. */
	void purge(Instant now) {
		clearLocation();
		shareLocation = false;
		contactName = null;
		contactPhone = null;
		shareTokenHash = null;
		purgedAt = now;
	}

	public String getDateId() {
		return key.dateId();
	}

	public String getUserId() {
		return key.userId();
	}

	public boolean isShareLocation() {
		return shareLocation;
	}

	public Double getLat() {
		return lat;
	}

	public Double getLon() {
		return lon;
	}

	public Instant getLocationAt() {
		return locationAt;
	}

	public Instant getCheckInDueAt() {
		return checkInDueAt;
	}

	public Instant getCheckInPromptedAt() {
		return checkInPromptedAt;
	}

	public Instant getCheckedInAt() {
		return checkedInAt;
	}

	public Instant getEscalatedAt() {
		return escalatedAt;
	}

	public EscalationReason getEscalationReason() {
		return escalationReason;
	}

	public Instant getEscalationResolvedAt() {
		return escalationResolvedAt;
	}

	public String getContactName() {
		return contactName;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public Instant getHomeSafeAt() {
		return homeSafeAt;
	}

	public Instant getDebriefAt() {
		return debriefAt;
	}

	public Instant getPurgedAt() {
		return purgedAt;
	}

	@Embeddable
	public record Key(@Column(name = "date_id") String dateId, @Column(name = "user_id") String userId)
			implements Serializable {
	}
}
