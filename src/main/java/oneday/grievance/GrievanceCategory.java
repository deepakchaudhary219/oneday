package oneday.grievance;

import java.time.Duration;

/** What a grievance is about, and how long the Grievance Officer has to resolve it. */
public enum GrievanceCategory {

	/** Intimate images of the complainant shared without consent: removed within 24 h (IT Rules 3(2)(b)). */
	INTIMATE_IMAGERY(Duration.ofHours(24), Escalation.APPELLATE_COMMITTEE),
	/** Content the complainant wants removed as unlawful or against our rules: 72 h (IT Rules 3(2)(a)(i)). */
	CONTENT_REMOVAL(Duration.ofHours(72), Escalation.APPELLATE_COMMITTEE),
	/** An appeal against a moderation decision about the complainant: a warning, a removal, a suspension. */
	ACCOUNT_ACTION(Duration.ofDays(15), Escalation.APPELLATE_COMMITTEE),
	/** Data rights under the DPDP Act: access, correction, erasure, consent. */
	PRIVACY(Duration.ofDays(15), Escalation.DATA_PROTECTION_BOARD),
	/** Anything else about the service. */
	OTHER(Duration.ofDays(15), Escalation.APPELLATE_COMMITTEE);

	/** Where a complainant who is not satisfied can go next. */
	public enum Escalation {
		/** Grievance Appellate Committee, within 30 days of our decision (IT Rules 3A). */
		APPELLATE_COMMITTEE,
		/** Data Protection Board of India, once our process is exhausted (DPDP Act s.13(3)). */
		DATA_PROTECTION_BOARD
	}

	private final Duration resolveWithin;

	private final Escalation escalation;

	GrievanceCategory(Duration resolveWithin, Escalation escalation) {
		this.resolveWithin = resolveWithin;
		this.escalation = escalation;
	}

	public Duration resolveWithin() {
		return resolveWithin;
	}

	public Escalation escalation() {
		return escalation;
	}
}
