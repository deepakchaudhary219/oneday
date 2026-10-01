package oneday.prompts;

import java.time.Instant;
import java.time.LocalDate;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A festival window (e.g. Onam for IN-KL) that shapes Today's Prompt and the Story Map while it lasts. */
@Entity
@Table(name = "festival_seasons")
public class FestivalSeason {

	@Id
	private String id;

	@Column(nullable = false)
	private String name;

	private String homeRegion;

	@Column(nullable = false)
	private LocalDate startsOn;

	@Column(nullable = false)
	private LocalDate endsOn;

	@Column(nullable = false)
	private String promptText;

	private String activityHint;

	@Column(nullable = false)
	private String createdBy;

	@Column(nullable = false)
	private Instant createdAt;

	protected FestivalSeason() {
	}

	FestivalSeason(String name, String homeRegion, LocalDate startsOn, LocalDate endsOn, String promptText,
			String activityHint, String createdBy, Instant now) {
		this.id = Ids.newId();
		this.name = name;
		this.homeRegion = homeRegion;
		this.startsOn = startsOn;
		this.endsOn = endsOn;
		this.promptText = promptText;
		this.activityHint = activityHint;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getHomeRegion() {
		return homeRegion;
	}

	public LocalDate getStartsOn() {
		return startsOn;
	}

	public LocalDate getEndsOn() {
		return endsOn;
	}

	public String getPromptText() {
		return promptText;
	}

	public String getActivityHint() {
		return activityHint;
	}
}
