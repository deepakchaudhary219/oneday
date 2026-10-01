package oneday.empathy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.Resource;

/**
 * Matches normalised words and phrases against a lexicon (English, Hinglish and Devanagari). Lines are
 * {@code TONE|term}, or {@code TONE~|term} for a mild word that counts only when the message addresses the
 * reader ("you're so stupid", not "this stupid rain"), or {@code YOU|term} for a second-person marker.
 *
 * <p>
 * Normalisation undoes the usual evasions: case, look-alike digits and symbols ({@code 1d10t}), stretched
 * letters ({@code stuuupid}), invisible characters and punctuation between letters. When several tones match,
 * the most severe wins (the declaration order of {@link Tone}).
 */
public class LexiconToneClassifier implements ToneClassifier {

	private record Term(Tone tone, boolean addressedOnly) {
	}

	private final Map<String, Term> words = new HashMap<>();

	private final List<Map.Entry<String, Tone>> phrases = new ArrayList<>();

	private final Set<String> secondPerson = new HashSet<>();

	public LexiconToneClassifier(Resource lexicon) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(lexicon.getInputStream(), StandardCharsets.UTF_8))) {
			reader.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(this::add);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Can't read the Empathy Mirror lexicon " + lexicon, ex);
		}
	}

	private void add(String line) {
		int bar = line.indexOf('|');
		String kind = line.substring(0, bar).strip();
		String term = normalize(line.substring(bar + 1));
		if (term.isEmpty()) {
			return;
		}
		if (kind.equals("YOU")) {
			secondPerson.add(term);
			return;
		}
		boolean addressedOnly = kind.endsWith("~");
		Tone tone = Tone.valueOf(addressedOnly ? kind.substring(0, kind.length() - 1) : kind);
		if (term.contains(" ")) {
			phrases.add(Map.entry(term, tone));
		}
		else {
			words.put(term, new Term(tone, addressedOnly));
		}
	}

	@Override
	public Optional<Tone> classify(String text) {
		String normalized = normalize(text);
		if (normalized.isEmpty()) {
			return Optional.empty();
		}
		String[] tokens = normalized.split(" ");
		boolean addressed = false;
		for (String token : tokens) {
			addressed |= secondPerson.contains(token);
		}
		Tone found = null;
		for (String token : tokens) {
			Term term = words.get(token);
			if (term != null && (addressed || !term.addressedOnly())) {
				found = severer(found, term.tone());
			}
		}
		String padded = " " + normalized + " ";
		for (Map.Entry<String, Tone> phrase : phrases) {
			if (padded.contains(" " + phrase.getKey() + " ")) {
				found = severer(found, phrase.getValue());
			}
		}
		return Optional.ofNullable(found);
	}

	static String normalize(String raw) {
		String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(text.length());
		int[] cps = text.codePoints()
			.filter(cp -> cp != 0x200B && cp != 0x200C && cp != 0x200D && cp != 0xFEFF && cp != '*' && cp != '\'')
			.toArray(); // invisible characters, "f*ck", "you're"
		int previous = ' ';
		for (int i = 0; i < cps.length; i++) {
			int cp = cps[i];
			// Symbols stand in for letters only inside a word ("sh!t"), not as punctuation ("stupid!!").
			boolean inWord = i + 1 < cps.length && Character.isLetterOrDigit(cps[i + 1]);
			int c = switch (cp) {
				case '0' -> 'o';
				case '1' -> 'i';
				case '3' -> 'e';
				case '4' -> 'a';
				case '5' -> 's';
				case '7' -> 't';
				case '!' -> inWord ? 'i' : cp;
				case '@' -> inWord ? 'a' : cp;
				case '$' -> inWord ? 's' : cp;
				default -> cp;
			};
			int type = Character.getType(c);
			if (!Character.isLetter(c) && type != Character.NON_SPACING_MARK
					&& type != Character.COMBINING_SPACING_MARK) {
				c = ' ';
			}
			if (c != previous) { // stretched letters and runs of separators collapse
				out.appendCodePoint(c);
				previous = c;
			}
		}
		return out.toString().strip();
	}

	private static Tone severer(Tone a, Tone b) {
		if (a == null) {
			return b;
		}
		return b == null || a.ordinal() >= b.ordinal() ? a : b;
	}
}
