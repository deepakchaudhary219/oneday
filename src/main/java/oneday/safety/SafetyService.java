package oneday.safety;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import oneday.common.ApiException;
import oneday.connections.ConnectionService;
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

	public SafetyService(BlockRepository blocks, ReportRepository reports, MomentService moments,
			SignalService signals, ConnectionService connections, UserGuard guard, Clock clock) {
		this.blocks = blocks;
		this.reports = reports;
		this.moments = moments;
		this.signals = signals;
		this.connections = connections;
		this.guard = guard;
		this.clock = clock;
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

	/** Erasure: remove blocks involving the user; keep reports but detach the reporter's identity. */
	@Transactional
	public void forget(String userId) {
		blocks.deleteInvolving(userId);
		reports.detachReporter(userId);
	}

	private String resolve(String userId, SafetyTarget target) {
		long provided = Stream.of(target.momentId(), target.signalId(), target.connectionId())
			.filter(v -> v != null && !v.isBlank())
			.count();
		if (provided != 1) {
			throw ApiException.badRequest("TARGET_REQUIRED", "Specify exactly one of momentId, signalId, connectionId");
		}
		String other;
		if (target.momentId() != null) {
			other = moments.find(target.momentId())
				.map(Moment::getOwnerId)
				.orElseThrow(() -> ApiException.notFound("Moment"));
		}
		else if (target.signalId() != null) {
			other = signals.counterpartOf(target.signalId(), userId);
		}
		else {
			other = connections.requireMember(target.connectionId(), userId).otherThan(userId);
		}
		if (other.equals(userId)) {
			throw ApiException.unprocessable("CANNOT_TARGET_SELF", "You can't block or report yourself");
		}
		return other;
	}

	public record SafetyTarget(String momentId, String signalId, String connectionId) {

		String type() {
			return momentId != null ? "MOMENT" : signalId != null ? "SIGNAL" : "CONNECTION";
		}
	}

	public record ReportReceipt(String reportId, String status, String message) {
	}
}
