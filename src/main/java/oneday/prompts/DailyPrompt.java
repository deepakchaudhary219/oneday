package oneday.prompts;

import java.time.Instant;
import java.time.LocalDate;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A prompt scheduled by staff for a day, for everyone or for one home region (a Roots prompt). */
@Entity
@Table(name = "daily_prompts")
public class DailyPrompt {

	@Id
	private String id;

	@Column(nullable = false)
	private LocalDate promptDate;

	private String homeRegion;

	@Column(nullable = false)
	private String text;

	private String activityHint;

	@Column(nullable = false)
	private String createdBy;

	@Column(nullable = false)
	private Instant createdAt;

	protected DailyPrompt() {
	}

	DailyPrompt(LocalDate promptDate, String homeRegion, String text, String activityHint, String createdBy,
			Instant now) {
		this.id = Ids.newId();
		this.promptDate = promptDate;
		this.homeRegion = homeRegion;
		this.text = text;
		this.activityHint = activityHint;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	public String getId() {
		return id;
	}

	public LocalDate getPromptDate() {
		return promptDate;
	}

	public String getHomeRegion() {
		return homeRegion;
	}

	public String getText() {
		return text;
	}

	public String getActivityHint() {
		return activityHint;
	}
}
