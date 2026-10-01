package oneday.pulsestatus;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A person's current status. Replaced in place on update, deleted when cleared or soon after expiry. */
@Entity
@Table(name = "pulse_statuses")
public class PulseStatus {

	@Id
	private String userId;

	@Column(nullable = false, unique = true)
	private String id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Mood mood;

	@Column(nullable = false)
	private String emoji;

	private String note;

	private String spotifyTrackId;

	private String musicTitle;

	@Column(nullable = false)
	private Instant setAt;

	@Column(nullable = false)
	private Instant expiresAt;

	protected PulseStatus() {
	}

	PulseStatus(String userId) {
		this.userId = userId;
	}

	void set(Mood mood, String emoji, String note, String spotifyTrackId, String musicTitle, Instant now,
			Instant expiresAt) {
		this.id = Ids.newId();
		this.mood = mood;
		this.emoji = emoji;
		this.note = note;
		this.spotifyTrackId = spotifyTrackId;
		this.musicTitle = spotifyTrackId == null ? null : musicTitle;
		this.setAt = now;
		this.expiresAt = expiresAt;
	}

	boolean isLive(Instant now) {
		return expiresAt.isAfter(now);
	}

	public String getUserId() {
		return userId;
	}

	public String getId() {
		return id;
	}

	public Mood getMood() {
		return mood;
	}

	public String getEmoji() {
		return emoji;
	}

	public String getNote() {
		return note;
	}

	public String getSpotifyTrackId() {
		return spotifyTrackId;
	}

	public String getMusicTitle() {
		return musicTitle;
	}

	public Instant getSetAt() {
		return setAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
