package oneday.profile;

/**
 * The Values Compass (blueprint v2 §7.1). Curated on purpose: values explain why two people were
 * surfaced to each other; they are never a score and never an exclusion filter.
 *
 * <p>
 * Deliberately absent (blueprint v2 §7.2): caste, skin tone, income, religion as an identity. Only
 * {@link #FAITH_MATTERS} exists, describing how much faith matters, not which faith.
 */
public enum CoreValue {

	KINDNESS("kindness"), HONESTY("honesty"), AMBITION("ambition"), ADVENTURE("adventure"), FAMILY("family"),
	HUMOUR("humour"), CURIOSITY("curiosity"), INDEPENDENCE("independence"), CREATIVITY("creativity"),
	FITNESS("fitness"), LEARNING("learning"), COMMUNITY("community"), SUSTAINABILITY("sustainability"),
	FAITH_MATTERS("faith");

	private final String label;

	CoreValue(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
