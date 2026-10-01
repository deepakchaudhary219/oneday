package oneday.staff;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import oneday.common.ApiException;
import oneday.grievance.Grievance;
import oneday.grievance.GrievanceService;
import oneday.identity.AccountAdministration;
import oneday.identity.AccountAdministration.VerificationDecision;
import oneday.identity.User;
import oneday.notify.Notice;
import oneday.notify.NotificationService;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.Report;
import oneday.safety.SafetyService;
import oneday.verification.VerificationAttempt;
import oneday.verification.VerificationService;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Trust & Safety console: manual verification review, the report queue with response-time targets,
 * account suspension, and staff management. Every decision is written to the append-only audit log.
 */
@Service
public class StaffConsoleService {

	public enum ReportAction {
		DISMISS, WARN, SUSPEND_USER
	}

	private final StaffDirectory directory;

	private final StaffRepository staff;

	private final StaffActionRepository audit;

	private final AccountAdministration accounts;

	private final VerificationService verification;

	private final SafetyService safety;

	private final ProfileService profiles;

	private final StaffProperties properties;

	private final NotificationService notifications;

	private final GrievanceService grievances;

	private final Clock clock;

	public StaffConsoleService(StaffDirectory directory, StaffRepository staff, StaffActionRepository audit,
			AccountAdministration accounts, VerificationService verification, SafetyService safety,
			ProfileService profiles, StaffProperties properties, NotificationService notifications,
			GrievanceService grievances, Clock clock) {
		this.directory = directory;
		this.staff = staff;
		this.audit = audit;
		this.accounts = accounts;
		this.verification = verification;
		this.safety = safety;
		this.profiles = profiles;
		this.properties = properties;
		this.notifications = notifications;
		this.grievances = grievances;
		this.clock = clock;
	}

	// ---- manual verification review ----------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ReviewItem> verificationQueue(String staffId) {
		directory.require(staffId, StaffRole.MODERATOR);
		return accounts.awaitingReview().stream().map(this::toReviewItem).toList();
	}

	@Transactional
	public ReviewItem decideVerification(String staffId, String userId, VerificationDecision decision, String note) {
		directory.require(staffId, StaffRole.MODERATOR);
		User user = accounts.decideVerification(userId, decision);
		record(staffId, "VERIFICATION_" + decision, "USER", userId, note);
		return toReviewItem(user);
	}

	// ---- report queue ------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ReportItem> reportQueue(String staffId) {
		directory.require(staffId, StaffRole.MODERATOR);
		return safety.openQueue().stream().map(this::toReportItem).toList();
	}

	@Transactional
	public ReportItem claim(String staffId, String reportId) {
		directory.require(staffId, StaffRole.MODERATOR);
		Report report = safety.claim(reportId, staffId);
		record(staffId, "REPORT_CLAIMED", "REPORT", reportId, null);
		return toReportItem(report);
	}

	@Transactional
	public ReportItem resolve(String staffId, String reportId, ReportAction action, String note) {
		directory.require(staffId, StaffRole.MODERATOR);
		Report.Resolution resolution = switch (action) {
			case DISMISS -> Report.Resolution.DISMISSED;
			case WARN -> Report.Resolution.WARNED;
			case SUSPEND_USER -> Report.Resolution.SUSPENDED;
		};
		Report report = safety.resolve(reportId, resolution, note, staffId);
		notifyOutcome(report, action);
		if (action == ReportAction.SUSPEND_USER) {
			accounts.suspend(report.getReportedId());
			record(staffId, "ACCOUNT_SUSPENDED", "USER", report.getReportedId(), "report " + reportId);
		}
		record(staffId, "REPORT_" + resolution, "REPORT", reportId, note);
		return toReportItem(report);
	}

	// ---- grievances (the Grievance Officer's desk) -------------------------------------------------

	/** Open grievances, nearest deadline first, with overdue ones flagged. */
	@Transactional(readOnly = true)
	public List<GrievanceItem> grievanceQueue(String staffId) {
		directory.require(staffId, StaffRole.MODERATOR);
		return grievances.openQueue().stream().map(this::toGrievanceItem).toList();
	}

	/**
	 * The response goes to the complainant as written, with where to go next if they are not satisfied.
	 * Reinstating a suspended account after an upheld appeal is a separate, admin-only action.
	 */
	@Transactional
	public GrievanceItem answerGrievance(String staffId, String grievanceId, Grievance.Outcome outcome,
			String response) {
		directory.require(staffId, StaffRole.MODERATOR);
		Grievance grievance = grievances.resolve(grievanceId, outcome, response, staffId);
		record(staffId, "GRIEVANCE_" + outcome, "GRIEVANCE", grievanceId, grievance.getReference());
		return toGrievanceItem(grievance);
	}

	/**
	 * Accounts whose holder asked for erasure while under a safety hold. Their records are preserved for
	 * review and erased automatically once the hold lifts.
	 */
	@Transactional(readOnly = true)
	public List<PendingErasure> pendingErasures(String staffId) {
		directory.require(staffId, StaffRole.MODERATOR);
		return accounts.pendingErasures()
			.stream()
			.map(u -> new PendingErasure(u.getId(), displayName(u.getId()), u.getErasureRequestedAt(),
					safety.holdReason(u.getId()).orElse("Hold lifted; erasure runs on the next sweep")))
			.toList();
	}

	// ---- admin: accounts, staff, audit -------------------------------------------------------------

	@Transactional
	public void reinstate(String adminId, String userId, String note) {
		directory.require(adminId, StaffRole.ADMIN);
		accounts.reinstate(userId);
		record(adminId, "ACCOUNT_REINSTATED", "USER", userId, note);
	}

	@Transactional(readOnly = true)
	public List<StaffMemberView> members(String adminId) {
		directory.require(adminId, StaffRole.ADMIN);
		return staff.findAll().stream()
			.map(m -> new StaffMemberView(m.getUserId(), displayName(m.getUserId()), m.getRole(), m.getGrantedAt()))
			.toList();
	}

	@Transactional
	public StaffMemberView grant(String adminId, String userId, StaffRole role) {
		directory.require(adminId, StaffRole.ADMIN);
		if (adminId.equals(userId)) {
			throw ApiException.conflict("CANNOT_CHANGE_OWN_ROLE", "Ask another admin to change your role");
		}
		accounts.require(userId);
		Instant now = clock.instant();
		StaffMember member = staff.findById(userId).orElse(null);
		if (member == null) {
			member = staff.save(new StaffMember(userId, role, adminId, now));
		}
		else {
			member.changeRole(role, adminId, now);
		}
		record(adminId, "STAFF_GRANTED_" + role, "USER", userId, null);
		return new StaffMemberView(userId, displayName(userId), role, now);
	}

	@Transactional
	public void revoke(String adminId, String userId) {
		directory.require(adminId, StaffRole.ADMIN);
		if (adminId.equals(userId)) {
			throw ApiException.conflict("CANNOT_CHANGE_OWN_ROLE", "Ask another admin to change your role");
		}
		if (staff.existsById(userId)) {
			staff.deleteById(userId);
			record(adminId, "STAFF_REVOKED", "USER", userId, null);
		}
	}

	@Transactional(readOnly = true)
	public List<AuditItem> audit(String adminId, int limit) {
		directory.require(adminId, StaffRole.ADMIN);
		return audit.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.clamp(limit, 1, 200)))
			.stream()
			.map(a -> new AuditItem(a.getStaffUserId(), a.getAction(), a.getSubjectType(), a.getSubjectId(),
					a.getNote(), a.getCreatedAt()))
			.toList();
	}

	// ---- helpers ------------------------------------------------------------------------------------

	/**
	 * The warned person learns why; the reporter learns that their report was handled, but never the
	 * specific penalty (that is the reported person's business).
	 */
	private void notifyOutcome(Report report, ReportAction action) {
		if (action == ReportAction.WARN) {
			notifications.notice(report.getReportedId(), Notice.Kind.WARNING,
					"Our safety team reviewed a report about your activity (" + describe(report.getCategory())
							+ ") and found it went against our community guidelines. Please keep OneDay safe and "
							+ "respectful: repeated issues can lead to suspension.");
		}
		if (report.getReporterId() != null) {
			notifications.notice(report.getReporterId(), Notice.Kind.REPORT_UPDATE, action == ReportAction.DISMISS
					? "Thanks for your report. We reviewed it and didn't find a breach of our guidelines this time. "
							+ "You can always block anyone, at any time."
					: "Thanks for your report. We reviewed it and took action.");
		}
	}

	private static String describe(oneday.safety.ReportCategory category) {
		return category.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
	}

	private void record(String staffId, String action, String subjectType, String subjectId, String note) {
		audit.save(new StaffAction(staffId, action, subjectType, subjectId,
				note == null || note.isBlank() ? null : note.strip(), clock.instant()));
	}

	private ReviewItem toReviewItem(User user) {
		Optional<VerificationAttempt> latest = verification.latestAttempt(user.getId());
		int declaredAge = Period.between(user.getDateOfBirth(), LocalDate.now(clock.withZone(ZoneOffset.UTC)))
			.getYears();
		return new ReviewItem(user.getId(), displayName(user.getId()), declaredAge, user.getVerificationStatus().name(),
				user.getAccountStatus().name(), latest.map(a -> a.getOutcome().name()).orElse(null),
				latest.map(VerificationAttempt::getEstimatedAge).orElse(null),
				latest.map(VerificationAttempt::getConfidence).orElse(null),
				latest.map(VerificationAttempt::getCreatedAt).orElse(null));
	}

	private ReportItem toReportItem(Report report) {
		Instant dueAt = report.getCreatedAt().plus(properties.slaFor(report.getPriority()));
		boolean overdue = report.getStatus().isOpen() && clock.instant().isAfter(dueAt);
		return new ReportItem(report.getId(), report.getCategory().name(), report.getPriority().name(),
				report.getStatus().name(), report.getReportedId(), displayName(report.getReportedId()),
				report.getReporterId(), report.getTargetType(), report.getDetails(), report.getCreatedAt(), dueAt,
				overdue, report.getAssigneeId(), safety.reportsAgainst(report.getReportedId()),
				report.getResolution() == null ? null : report.getResolution().name());
	}

	private GrievanceItem toGrievanceItem(Grievance g) {
		return new GrievanceItem(g.getId(), g.getReference(), g.getCategory().name(), g.getStatus().name(),
				g.getUserId(), displayName(g.getUserId()), accounts.require(g.getUserId()).getAccountStatus().name(),
				g.getSubjectRef(), g.getDescription(), g.getCreatedAt(), g.getResolveBy(),
				g.isOverdue(clock.instant()), g.getOutcome() == null ? null : g.getOutcome().name());
	}

	private String displayName(String userId) {
		return profiles.find(userId).map(Profile::getDisplayName).orElse(null);
	}

	public record ReviewItem(String userId, String displayName, int declaredAge, String verificationStatus,
			String accountStatus, String latestOutcome, Integer estimatedAge, Double confidence, Instant attemptedAt) {
	}

	/** {@code reportedDisplayName} is null when the reported account has since been erased. */
	public record ReportItem(String id, String category, String priority, String status, String reportedUserId,
			String reportedDisplayName, String reporterUserId, String targetType, String details, Instant createdAt,
			Instant dueAt, boolean overdue, String assigneeId, long reportsAgainstUser, String resolution) {
	}

	public record GrievanceItem(String id, String reference, String category, String status, String userId,
			String displayName, String accountStatus, String subjectRef, String description, Instant filedAt,
			Instant resolveBy, boolean overdue, String outcome) {
	}

	public record PendingErasure(String userId, String displayName, Instant requestedAt, String holdReason) {
	}

	public record StaffMemberView(String userId, String displayName, StaffRole role, Instant grantedAt) {
	}

	public record AuditItem(String staffUserId, String action, String subjectType, String subjectId, String note,
			Instant at) {
	}
}
