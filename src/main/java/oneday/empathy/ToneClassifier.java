package oneday.empathy;

import java.util.Optional;

/**
 * Reads the likely tone of a short message. The default is a lexicon ({@link LexiconToneClassifier}); a model
 * can replace it as a bean of this type. False positives are cheap (the sender can send anyway), so a
 * classifier should favour recall on the severe tones.
 */
public interface ToneClassifier {

	Optional<Tone> classify(String text);
}
