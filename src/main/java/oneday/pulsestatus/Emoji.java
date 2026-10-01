package oneday.pulsestatus;

import java.util.Optional;

/** Validates the status emoji: one to three emoji, including ZWJ sequences, skin tones, keycaps and flags. */
final class Emoji {

	private static final int MAX_CHARS = 32;

	private static final int MAX_EMOJI = 3;

	private Emoji() {
	}

	static Optional<String> normalize(String raw) {
		String value = raw == null ? "" : raw.strip();
		if (value.isEmpty() || value.length() > MAX_CHARS) {
			return Optional.empty();
		}
		int pictographs = 0;
		int regionalIndicators = 0;
		boolean joined = false;
		for (int cp : value.codePoints().toArray()) {
			if (Character.isExtendedPictographic(cp)) {
				pictographs += joined ? 0 : 1; // a ZWJ sequence such as a family is one emoji
			}
			else if (cp >= 0x1F1E6 && cp <= 0x1F1FF) {
				regionalIndicators++;
			}
			else if (!(cp == 0x200D || cp == 0xFE0F || cp == 0x20E3 || Character.isEmojiModifier(cp)
					|| Character.isEmojiComponent(cp))) {
				return Optional.empty();
			}
			joined = cp == 0x200D;
		}
		int count = pictographs + (regionalIndicators + 1) / 2;
		return count >= 1 && count <= MAX_EMOJI ? Optional.of(value) : Optional.empty();
	}
}
