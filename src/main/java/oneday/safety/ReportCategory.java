package oneday.safety;

/**
 * Categories mapped to documented harm patterns (blueprint §31.2) so Trust & Safety can route and
 * prioritise. P0 items (possible minors, intimate imagery, threats) are reviewed first; possible-minor
 * reports also trigger the POCSO evidence-preservation process (tech arch v2 §8).
 */
public enum ReportCategory {

	UNDERAGE_SUSPECTED(Priority.P0), NON_CONSENSUAL_INTIMATE_IMAGERY(Priority.P0), THREAT_OR_VIOLENCE(Priority.P0),
	UNSOLICITED_EXPLICIT_CONTENT(Priority.P1), PERSISTENT_CONTACT_AFTER_DECLINE(Priority.P1),
	IMPERSONATION(Priority.P1), HARASSMENT(Priority.P1), SCAM_OR_FRAUD(Priority.P1), HATE(Priority.P1),
	SPAM(Priority.P2), OTHER(Priority.P2);

	public enum Priority {
		P0, P1, P2
	}

	private final Priority priority;

	ReportCategory(Priority priority) {
		this.priority = priority;
	}

	public Priority priority() {
		return priority;
	}
}
