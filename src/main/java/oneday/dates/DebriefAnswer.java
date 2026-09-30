package oneday.dates;

/**
 * Mutual Debrief answers (blueprint v2 §5.3, §24.2). Only answers both people gave are revealed, and only
 * the {@link #shared} ones: the safety answers are never shown to the other person. Leaving out a safety
 * answer privately offers the Trust & Safety route instead.
 */
public enum DebriefAnswer {

	HAD_A_GOOD_TIME(true, "You both had a good time"),
	GREAT_CONVERSATION(true, "You both loved the conversation"),
	WANT_TO_MEET_AGAIN(true, "You'd both like to meet again"),
	FELT_SAFE(false, null),
	FELT_RESPECTED(false, null);

	private final boolean shared;

	private final String overlapLine;

	DebriefAnswer(boolean shared, String overlapLine) {
		this.shared = shared;
		this.overlapLine = overlapLine;
	}

	public boolean shared() {
		return shared;
	}

	public String overlapLine() {
		return overlapLine;
	}
}
