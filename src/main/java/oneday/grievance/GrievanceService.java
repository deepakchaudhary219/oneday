package oneday.grievance;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.grievance.Grievance.Outcome;
import oneday.identity.UserGuard;
import oneday.notify.Notice;
import oneday.notify.NotificationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grievance redressal. Anyone with an account can file, including a suspended account appealing its
 * suspension. Filing is the acknowledgement: the complainant gets a reference and a deadline at once, and a
 * notice keeps the record. The Grievance Officer answers from the staff console (see
 * {@code StaffConsoleService}); the answer tells the complainant where to go next if they are not satisfied.
 */
@Service
public class GrievanceService {

	/** Crockford base32 without look-alikes: references are read out over the phone. */
	private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
		.withZone(ZoneId.of("Asia/Kolkata"));

	private final GrievanceRepository grievances;

	private final UserGuard guard;

	private final RateLimiter rateLimiter;

	private final NotificationService notifications;

	private final GrievanceProperties properties;

	private final Clock clock;

	public GrievanceService(GrievanceRepository grievances, UserGuard guard, RateLimiter rateLimiter,
			NotificationService notifications, GrievanceProperties properties, Clock clock) {
		this.grievances = grievances;
		this.guard = guard;
		this.rateLimiter = rateLimiter;
		this.notifications = notifications;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public GrievanceView file(String userId, GrievanceCategory category, String subjectRef, String description) {
		guard.requireExisting(userId);
		if (!rateLimiter.tryAcquire("grievance:" + userId, properties.perUserPerDay(), Duration.ofDays(1))) {
			throw ApiException.tooManyRequests("GRIEVANCE_RATE_LIMITED",
					"You've filed several grievances today. We'll answer those first; you can add more tomorrow.");
		}
		Grievance grievance = grievances.save(new Grievance(newReference(), userId, category,
				blankToNull(subjectRef), description.strip(), clock.instant()));
		notifications.notice(userId, Notice.Kind.GRIEVANCE_UPDATE, "We received your grievance "
				+ grievance.getReference() + " and will respond by " + DAY.format(grievance.getResolveBy()) + " IST.");
		return GrievanceView.of(grievance);
	}

	@Transactional(readOnly = true)
	public List<GrievanceView> mine(String userId) {
		guard.requireExisting(userId);
		return grievances.findByUserIdOrderByCreatedAtDesc(userId).stream().map(GrievanceView::of).toList();
	}

	/** Open grievances, nearest deadline first. Callers must be staff (see {@code StaffConsoleService}). */
	@Transactional(readOnly = true)
	public List<Grievance> openQueue() {
		return grievances.findByStatusOrderByResolveByAsc(Grievance.Status.OPEN);
	}

	@Transactional
	public Grievance resolve(String grievanceId, Outcome outcome, String response, String staffId) {
		Grievance grievance = grievances.findById(grievanceId).orElseThrow(() -> ApiException.notFound("Grievance"));
		if (grievance.getStatus() == Grievance.Status.RESOLVED) {
			throw ApiException.conflict("ALREADY_RESOLVED", "This grievance has already been answered");
		}
		grievance.resolve(outcome, response.strip(), staffId, clock.instant());
		notifications.notice(grievance.getUserId(), Notice.Kind.GRIEVANCE_UPDATE, "We've answered your grievance "
				+ grievance.getReference() + ". Open Help > Grievances to read the response. "
				+ nextStep(grievance.getCategory()));
		return grievance;
	}

	public OfficerContact officer() {
		return new OfficerContact(blankToNull(properties.officerName()), blankToNull(properties.officerEmail()),
				blankToNull(properties.officerAddress()));
	}

	@Transactional(readOnly = true)
	public List<Grievance> filedBy(String userId) {
		return grievances.findByUserIdOrderByCreatedAtDesc(userId);
	}

	/** Erasure: the complainant's grievances go with the account. */
	@Transactional
	public void forget(String userId) {
		grievances.deleteByUserId(userId);
	}

	static String nextStep(GrievanceCategory category) {
		return switch (category.escalation()) {
			case APPELLATE_COMMITTEE -> "If you're not satisfied, you can appeal to the Grievance Appellate "
					+ "Committee at gac.gov.in within 30 days.";
			case DATA_PROTECTION_BOARD -> "If you're not satisfied, you can complain to the Data Protection Board "
					+ "of India.";
		};
	}

	private String newReference() {
		String reference;
		do {
			StringBuilder sb = new StringBuilder("G-");
			for (int i = 0; i < 8; i++) {
				sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
			}
			reference = sb.toString();
		}
		while (grievances.existsByReference(reference));
		return reference;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public record GrievanceView(String id, String reference, GrievanceCategory category, String subjectRef,
			String description, Grievance.Status status, Instant filedAt, Instant resolveBy, Instant resolvedAt,
			Outcome outcome, String response, String ifNotSatisfied) {

		static GrievanceView of(Grievance g) {
			return new GrievanceView(g.getId(), g.getReference(), g.getCategory(), g.getSubjectRef(),
					g.getDescription(), g.getStatus(), g.getCreatedAt(), g.getResolveBy(), g.getResolvedAt(),
					g.getOutcome(), g.getResponse(),
					g.getStatus() == Grievance.Status.RESOLVED ? nextStep(g.getCategory()) : null);
		}
	}

	public record OfficerContact(String name, String email, String address) {
	}
}
