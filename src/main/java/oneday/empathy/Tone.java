package oneday.empathy;

/** Why a message might land badly, with the reflection shown to the sender and the report it maps to. */
public enum Tone {

	INSULT("This might sting. Is there a kinder way to say it?", "HARASSMENT"),
	BODY_SHAMING("Comments about someone's body often hurt more than they seem. Want to rephrase?", "HARASSMENT"),
	SEXUAL_PRESSURE("This might feel like pressure. Explicit requests land best once you've both said you're comfortable.",
			"UNSOLICITED_EXPLICIT_CONTENT"),
	THREAT("This could read as a threat, and threats get accounts suspended. Want to rethink it?", "THREAT_OR_VIOLENCE"),
	HATE("This targets who someone is. OneDay doesn't allow hate. Want to rephrase?", "HATE");

	private final String reflection;

	private final String reportCategory;

	Tone(String reflection, String reportCategory) {
		this.reflection = reflection;
		this.reportCategory = reportCategory;
	}

	public String reflection() {
		return reflection;
	}

	/** The {@code ReportCategory} offered to the recipient. */
	public String reportCategory() {
		return reportCategory;
	}
}
