package oneday.dates;

public enum EscalationReason {
	/** One-tap SOS (alongside dialling 112 on the phone). */
	SOS,
	/** Answered "Going OK?" with "I need help". */
	HELP_REQUESTED,
	/** Did not answer "Going OK?" within the grace period. */
	MISSED_CHECK_IN
}
