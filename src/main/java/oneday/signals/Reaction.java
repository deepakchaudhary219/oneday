package oneday.signals;

/**
 * Four specific reactions instead of a generic like (blueprint §30.2): each carries real information
 * and makes the sender notice their actual response. There is deliberately no free-text option.
 */
public enum Reaction {

	RESONATES("this resonates"), MADE_ME_SMILE("made me smile"), WANT_TO_KNOW_MORE("want to know more"),
	SAME_HERE("same here");

	private final String label;

	Reaction(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
