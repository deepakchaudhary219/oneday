package oneday.empathy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LexiconToneClassifierTest {

	private final LexiconToneClassifier classifier = new LexiconToneClassifier(
			new ClassPathResource("empathy/lexicon.txt"));

	@Test
	void ordinaryMessagesPass() {
		assertThat(classifier.classify("Chai at 6? The place near Cubbon Park")).isEmpty();
		assertThat(classifier.classify("this stupid rain ruined my plans")).isEmpty(); // not aimed at anyone
		assertThat(classifier.classify("I'd kill for a good dosa right now")).isEmpty();
		assertThat(classifier.classify("Pakistani food in Old Delhi is great")).isEmpty();
		assertThat(classifier.classify("we hit a chakka in gully cricket 😄")).isEmpty();
		assertThat(classifier.classify("")).isEmpty();
	}

	@Test
	void mildInsultsCountWhenAimedAtTheReader() {
		assertThat(classifier.classify("you're so stupid")).contains(Tone.INSULT);
		assertThat(classifier.classify("u r an 1d10t")).contains(Tone.INSULT);
		assertThat(classifier.classify("you are STUUUUPID!!!")).contains(Tone.INSULT);
		assertThat(classifier.classify("tu bewakoof hai")).contains(Tone.INSULT);
		assertThat(classifier.classify("you look ugly")).contains(Tone.BODY_SHAMING);
	}

	@Test
	void strongWordsAndPhrasesCountAnywhereAndTheMostSevereWins() {
		assertThat(classifier.classify("kamina saala")).contains(Tone.INSULT);
		assertThat(classifier.classify("f*ck you")).contains(Tone.INSULT);
		assertThat(classifier.classify("तू कमीना है")).contains(Tone.INSULT);
		assertThat(classifier.classify("send n.u.d.e.s")).isEmpty(); // spaced-out letters are beyond a lexicon
		assertThat(classifier.classify("send nudes")).contains(Tone.SEXUAL_PRESSURE);
		assertThat(classifier.classify("I know where you live")).contains(Tone.THREAT);
		assertThat(classifier.classify("tujhe dekh lunga")).contains(Tone.THREAT);
		assertThat(classifier.classify("you idiot, I'll kill you")).contains(Tone.THREAT);
		assertThat(classifier.classify("go back to pakistan")).contains(Tone.HATE);
	}

	@Test
	void normalisationUndoesCommonEvasions() {
		assertThat(LexiconToneClassifier.normalize("  Ｓｔｕｐｉｄ​!! ")).isEqualTo("stupid");
		assertThat(LexiconToneClassifier.normalize("1d10t")).isEqualTo("idiot");
		assertThat(LexiconToneClassifier.normalize("you're")).isEqualTo("youre");
	}
}
