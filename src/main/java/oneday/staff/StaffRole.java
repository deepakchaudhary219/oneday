package oneday.staff;

/** Trust & Safety roles. ADMIN can do everything a MODERATOR can, plus manage staff and read the audit log. */
public enum StaffRole {

	MODERATOR, ADMIN;

	public boolean covers(StaffRole required) {
		return this == ADMIN || this == required;
	}
}
