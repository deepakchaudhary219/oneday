package oneday.staff;

import java.time.Clock;
import java.util.EnumSet;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import oneday.grievance.GrievanceRepository;
import oneday.safety.Report;
import oneday.safety.ReportCategory.Priority;
import oneday.safety.ReportRepository;

import org.springframework.stereotype.Component;

/**
 * Work past its deadline, read at scrape time: the numbers to alert on, since an overdue P0 report or
 * grievance is a legal problem as well as a safety one.
 */
@Component
public class BacklogMetrics implements MeterBinder {

	private final ReportRepository reports;

	private final GrievanceRepository grievances;

	private final StaffProperties properties;

	private final Clock clock;

	public BacklogMetrics(ReportRepository reports, GrievanceRepository grievances, StaffProperties properties,
			Clock clock) {
		this.reports = reports;
		this.grievances = grievances;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		for (Priority priority : Priority.values()) {
			Gauge.builder("oneday.reports.overdue", () -> reports.countByPriorityAndStatusInAndCreatedAtBefore(priority,
					EnumSet.of(Report.Status.OPEN, Report.Status.IN_REVIEW),
					clock.instant().minus(properties.slaFor(priority))))
				.tag("priority", priority.name())
				.description("Open reports past their response target")
				.register(registry);
		}
		Gauge.builder("oneday.grievances.overdue",
				() -> grievances.countByStatusAndResolveByBefore(oneday.grievance.Grievance.Status.OPEN, clock.instant()))
			.description("Open grievances past their legal deadline")
			.register(registry);
	}
}
