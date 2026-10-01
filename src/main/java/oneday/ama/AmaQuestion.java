package oneday.ama;

import java.time.Instant;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ama_questions")
class AmaQuestion {

	@Id
	private String id;

	@Column(nullable = false)
	private String amaId;

	@Column(nullable = false)
	private String askerId;

	@Column(nullable = false)
	private String body;

	@Column(nullable = false)
	private boolean anonymous;

	@Column(nullable = false)
	private boolean hidden;

	private String toneFlag;

	private String answer;

	private Instant answeredAt;

	@Column(nullable = false)
	private Instant createdAt;

	protected AmaQuestion() {
	}

	AmaQuestion(String amaId, String askerId, String body, boolean anonymous, String toneFlag, Instant now) {
		this.id = Ids.newId();
		this.amaId = amaId;
		this.askerId = askerId;
		this.body = body;
		this.anonymous = anonymous;
		this.toneFlag = toneFlag;
		this.createdAt = now;
	}

	void answer(String text, Instant now) {
		this.answer = text;
		this.answeredAt = now;
	}

	void hide() {
		this.hidden = true;
	}

	String getId() {
		return id;
	}

	String getAmaId() {
		return amaId;
	}

	String getAskerId() {
		return askerId;
	}

	String getBody() {
		return body;
	}

	boolean isAnonymous() {
		return anonymous;
	}

	boolean isHidden() {
		return hidden;
	}

	String getToneFlag() {
		return toneFlag;
	}

	String getAnswer() {
		return answer;
	}

	Instant getAnsweredAt() {
		return answeredAt;
	}

	Instant getCreatedAt() {
		return createdAt;
	}
}
