package oneday.staff;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Appends to the staff audit log for staff work that lives outside the console service (e.g. Date Mode). */
@Component
public class StaffAudit {

	private final StaffActionRepository audit;

	private final Clock clock;

	public StaffAudit(StaffActionRepository audit, Clock clock) {
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public void record(String staffId, String action, String subjectType, String subjectId, String note) {
		audit.save(new StaffAction(staffId, action, subjectType, subjectId,
				note == null || note.isBlank() ? null : note.strip(), clock.instant()));
	}
}
