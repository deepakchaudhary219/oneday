package oneday.pulsestatus;

/** How someone is feeling right now, in words friends can read at a glance. */
public enum Mood {

	HAPPY("Happy"), CHILL("Chill"), FOCUSED("Heads down"), ADVENTUROUS("Up for adventure"), SOCIAL("Feeling social"),
	CELEBRATING("Celebrating"), ON_THE_MOVE("On the move"), TIRED("Tired"), HOMESICK("Missing home"),
	/** Friends see a gentle nudge to check in (a two-way moment), never a push notification. */
	LOW("Feeling low");

	private final String label;

	Mood(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
