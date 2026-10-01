package oneday.ama;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ama_votes")
class AmaVote {

	@Embeddable
	record Key(@Column(name = "question_id") String questionId, @Column(name = "voter_id") String voterId) {
	}

	@EmbeddedId
	private Key key;

	protected AmaVote() {
	}

	AmaVote(String questionId, String voterId) {
		this.key = new Key(questionId, voterId);
	}

	Key getKey() {
		return key;
	}
}
