package oneday.staff;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_members")
public class StaffMember {

	@Id
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StaffRole role;

	private String grantedBy;

	@Column(nullable = false)
	private Instant grantedAt;

	protected StaffMember() {
	}

	StaffMember(String userId, StaffRole role, String grantedBy, Instant now) {
		this.userId = userId;
		this.role = role;
		this.grantedBy = grantedBy;
		this.grantedAt = now;
	}

	void changeRole(StaffRole role, String grantedBy, Instant now) {
		this.role = role;
		this.grantedBy = grantedBy;
		this.grantedAt = now;
	}

	public String getUserId() {
		return userId;
	}

	public StaffRole getRole() {
		return role;
	}

	public Instant getGrantedAt() {
		return grantedAt;
	}
}
