package oneday.consent;

/**
 * The purposes OneDay asks consent for (DPDP s.5 notice, s.6 consent), each with a plain-language notice of
 * what is processed and what withdrawing does. Account-level processing needed to run the service at all is
 * covered by the signup consent ({@code users.consent_version}); erasure is how that one is withdrawn.
 */
public enum ConsentPurpose {

	LOCATION_DISCOVERY("Your current area (a ~0.7 km² cell, never coordinates) so nearby people can discover your stories",
			"Your stored area is deleted at once and you leave discovery, the Story Map and Right Now."),
	DATING_PREFERENCES("Your private Dating Lens, gender and who you're interested in, used only to lean discovery and to enable Sparks",
			"These fields are deleted, your sparks and Couple Mode confirmations are withdrawn."),
	ROOTS_AND_LANGUAGES("Your home region and languages, for Roots and language lenses",
			"Your home region and languages are deleted; Roots and language lenses switch off for you."),
	WELLBEING_SURVEY("Your answers to \"was your time well spent?\", used only in aggregate to keep the product honest",
			"Your answers are deleted and we stop asking.");

	private final String processes;

	private final String onWithdrawal;

	ConsentPurpose(String processes, String onWithdrawal) {
		this.processes = processes;
		this.onWithdrawal = onWithdrawal;
	}

	public String processes() {
		return processes;
	}

	public String onWithdrawal() {
		return onWithdrawal;
	}
}
