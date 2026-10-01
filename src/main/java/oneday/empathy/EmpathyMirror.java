package oneday.empathy;

import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The Empathy Mirror (blueprint v2 §10, behavioural layer): before a message that reads as unkind is sent, the
 * sender sees a short reflection and chooses to edit or send anyway. It is a nudge, not a filter: nothing is
 * blocked, and the recipient of a message sent anyway is asked "does this bother you?" with a one-tap report.
 *
 * <p>
 * Effectiveness is {@code 1 - sent_anyway / prompted} on {@code oneday_empathy_mirror{surface,outcome}}.
 */
@Component
public class EmpathyMirror {

	public static final String BOTHER_QUESTION = "Does this bother you?";

	private final ToneClassifier classifier;

	private final MeterRegistry metrics;

	EmpathyMirror(ToneClassifier classifier, MeterRegistry metrics) {
		this.classifier = classifier;
		this.metrics = metrics;
	}

	/**
	 * Throws 422 {@code EMPATHY_CHECK} (with {@code tone}) for a message that may land badly, unless the sender
	 * already saw the reflection and chose to send it anyway; then returns the tone to store on the message.
	 */
	public Optional<Tone> reflect(String surface, String body, boolean sendAnyway) {
		Optional<Tone> tone = classifier.classify(body);
		if (tone.isEmpty()) {
			return tone;
		}
		if (!sendAnyway) {
			metrics.counter("oneday.empathy_mirror", "surface", surface, "outcome", "prompted").increment();
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "EMPATHY_CHECK", tone.get().reflection())
				.with("tone", tone.get().name());
		}
		metrics.counter("oneday.empathy_mirror", "surface", surface, "outcome", "sent_anyway").increment();
		return tone;
	}

	/** What the recipient of a flagged message is offered, or null for an ordinary message or the sender. */
	public static Concern concernFor(String toneFlag, boolean mine) {
		if (toneFlag == null || mine) {
			return null;
		}
		return new Concern(BOTHER_QUESTION, Tone.valueOf(toneFlag).reportCategory());
	}

	public record Concern(String question, String reportCategory) {
	}
}
