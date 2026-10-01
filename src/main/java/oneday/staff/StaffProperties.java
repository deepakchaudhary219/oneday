package oneday.staff;

import java.time.Duration;
import java.util.Set;

import oneday.safety.ReportCategory.Priority;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bootstrapAdminUserIds user ids granted ADMIN without a database row, to create the first admin.
 * Ids, not emails: emails are not ownership-verified yet, so an email allowlist could be claimed by anyone.
 * @param slaP0 response target for P0 reports (possible minors, intimate imagery, threats)
 * @param slaP1 response target for P1 reports
 * @param slaP2 response target for P2 reports
 */
@ConfigurationProperties("oneday.staff")
public record StaffProperties(Set<String> bootstrapAdminUserIds, Duration slaP0, Duration slaP1, Duration slaP2) {

	public StaffProperties {
		bootstrapAdminUserIds = bootstrapAdminUserIds == null ? Set.of() : Set.copyOf(bootstrapAdminUserIds);
	}

	public Duration slaFor(Priority priority) {
		return switch (priority) {
			case P0 -> slaP0;
			case P1 -> slaP1;
			case P2 -> slaP2;
		};
	}
}
