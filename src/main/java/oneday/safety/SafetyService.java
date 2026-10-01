package oneday.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import oneday.common.ApiException;
import oneday.common.ProductMetrics;
import oneday.connections.ConnectionService;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
import oneday.identity.UserGuard;
import oneday.moments.Moment;
import oneday.moments.MomentService;
import oneday.signals.SignalService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Block and report. Targets are referenced by the thing the user saw (a moment, a signal or a
 * connection) because other people's internal ids are never exposed to clients.
 */
@Service
public class SafetyService {

	private final BlockRepository blocks;

	private final ReportRepository reports;

	private final MomentService moments;

	private final SignalService signals;

	private final ConnectionService connections;

	private final UserGuard guard;

	private final Clock clock;

	private final ProductMetrics metrics;

	private final EventPublisher events;

	private final SafetyProperties safetyProperties;

	private final List<SafetyTargetResolver> resolvers;

	public SafetyService(BlockRepository blocks, ReportRepository reports, MomentService moments,
			SignalService signals, ConnectionService connections, UserGuard guard, Clock clock,
			SafetyProperties safetyProperties, ProductMetrics metrics, EventPublisher events,
			List<SafetyTargetResolver> resolvers) {
		this.resolvers = List.copyOf(resolvers);
		this.metrics = metrics;
		this.events = events;
		this.blocks = blocks;
		this.reports = reports;
		this.moments = moments;
		this.signals = signals;
		this.connections = connections;
		this.guard = guard;
		this.clock = clock;
		this.safetyProperties = safetyProperties;
	}

	/**
	 * One tap, no notification to the other person. The block propagates: the connection ends, pending
	 * signals either way are archived, and both people disappear from each other's discovery.
	 */
	@Transactional
	public void block(String userId, SafetyTarget target) {
		guard.requireActive(userId);
		String other = resolve(userId, target);
		if (!blocks.existsByBlockerIdAndBlockedId(userId, other)) {
			blocks.save(new Block(userId, other, clock.instant()));
			events.publish(new DomainEvent.UserBlocked(userId, other));
		}
		connections.endBetween(userId, other);
		signals.archiveBetween(userId, other);
	}

	@Transactional
	public ReportReceipt report(String userId, SafetyTarget target, ReportCategory category, String details,
			boolean alsoBlock) {
		guard.requireActive(userId);
		String other = resolve(userId, target);
		Report report = reports.save(new Report(userId, other, category, target.type(),
				details == null || details.isBlank() ? null : details.strip(), clock.instant()));
		metrics.reportFiled(report.getPriority());
		if (alsoBlock) {
			block(userId, target);
		}
		return new ReportReceipt(report.getId(), "RECEIVED",
				"Thank you. Our safety team reviews the most serious reports first.");
	}

	@Transactional(readOnly = true)
	public List<Report> reportsFiledBy(String userId) {
		return reports.findByReporterIdOrderByCreatedAtDesc(userId);
	}

	@Transactional(readOnly = true)
	public List<Block> blocksMadeBy(String userId) {
		return blocks.findByBlockerId(userId);
	}

	// ---- Trust & Safety queue (staff only; callers enforce the staff role) ----------------------------

	/** Open work, most urgent first: priority, then oldest. */
	@Transactional(readOnly = true)
	public List<Report> openQueue() {
		return reports.findByStatusIn(List.of(Report.Status.OPEN, Report.Status.IN_REVIEW))
			.stream()
			.sorted(Comparator.comparing(Report::getPriority).thenComparing(Report::getCreatedAt))
			.toList();
	}

	@Transactional(readOnly = true)
	public long reportsAgainst(String userId) {
		return reports.countByReportedId(userId);
	}

	@Transactional
	public Report claim(String reportId, String staffUserId) {
		Report report = requireOpen(reportId);
		report.claim(staffUserId);
		return report;
	}

	@Transactional
	public Report resolve(String reportId, Report.Resolution resolution, String note, String staffUserId) {
		Report report = requireOpen(reportId);
		report.resolve(resolution, note, staffUserId, clock.instant());
		return report;
	}

	private Report requireOpen(String reportId) {
		Report report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Report"));
		if (!report.getStatus().isOpen()) {
			throw ApiException.conflict("REPORT_CLOSED", "This report has already been resolved");
		}
		return report;
	}

	/**
	 * Why this account's records must be kept even if its holder asks for erasure, or empty if they need
	 * not be: an open P0 report (possible minor, intimate imagery, threats), or an enforcement action within
	 * the evidence-retention period.
	 */
	@Transactional(readOnly = true)
	public Optional<String> holdReason(String userId) {
		if (reports.existsByReportedIdAndPriorityAndStatusIn(userId, ReportCategory.Priority.P0,
				List.of(Report.Status.OPEN, Report.Status.IN_REVIEW))) {
			return Optional.of("Open P0 safety report");
		}
		Instant since = clock.instant().minus(safetyProperties.evidenceRetention());
		return reports
			.findFirstByReportedIdAndResolutionAndResolvedAtAfterOrderByResolvedAtDesc(userId,
					Report.Resolution.SUSPENDED, since)
			.map(r -> "Enforcement evidence retained until "
					+ r.getResolvedAt().plus(safetyProperties.evidenceRetention()));
	}

	/** Erasure: remove blocks involving the user; keep reports but detach the reporter's identity. */
	@Transactional
	public void forget(String userId) {
		blocks.deleteInvolving(userId);
		reports.detachReporter(userId);
	}

	private String resolve(String userId, SafetyTarget target) {
		String other = switch (target.type()) {
			case "MOMENT" -> moments.find(target.id()).map(Moment::getOwnerId).orElseThrow(() -> ApiException.notFound("Moment"));
			case "SIGNAL" -> signals.counterpartOf(target.id(), userId);
			case "CONNECTION" -> connections.requireMember(target.id(), userId).otherThan(userId);
			default -> resolvers.stream()
				.filter(r -> r.type().equals(target.type()))
				.findFirst()
				.flatMap(r -> r.personBehind(userId, target.id()))
				.orElseThrow(() -> ApiException.notFound("Target"));
		};
		if (other.equals(userId)) {
			throw ApiException.unprocessable("CANNOT_TARGET_SELF", "You can't block or report yourself");
		}
		return other;
	}

	/** The request fields that can name a target: the core three plus every registered resolver's. */
	public Map<String, String> targetFields() {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("momentId", "MOMENT");
		fields.put("signalId", "SIGNAL");
		fields.put("connectionId", "CONNECTION");
		resolvers.forEach(r -> fields.put(r.field(), r.type()));
		return fields;
	}

	/** Builds the target from a request body: exactly one known id field must be present. */
	public SafetyTarget targetFrom(Map<String, Object> body) {
		Map<String, String> fields = targetFields();
		List<SafetyTarget> found = fields.entrySet()
			.stream()
			.filter(f -> body.get(f.getKey()) instanceof String v && !v.isBlank())
			.map(f -> new SafetyTarget(f.getValue(), ((String) body.get(f.getKey())).trim()))
			.toList();
		if (found.size() != 1) {
			throw ApiException.badRequest("TARGET_REQUIRED", "Specify exactly one of " + String.join(", ", fields.keySet()));
		}
		return found.get(0);
	}

	/** What is being blocked or reported: a type (MOMENT, PLAN, ...) and the id the viewer saw. */
	public record SafetyTarget(String type, String id) {
	}

	public record ReportReceipt(String reportId, String status, String message) {
	}
}
