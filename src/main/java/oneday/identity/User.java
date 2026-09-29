package oneday.identity;

import java.time.Instant;
import java.time.LocalDate;

import oneday.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	private String id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private LocalDate dateOfBirth;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus accountStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VerificationStatus verificationStatus;

	private Instant verifiedAt;

	/** DPDP: which version of the privacy notice the user accepted, and when. */
	@Column(nullable = false)
	private String consentVersion;

	@Column(nullable = false)
	private Instant consentedAt;

	@Column(nullable = false)
	private Instant createdAt;

	protected User() {
	}

	public User(String email, String passwordHash, LocalDate dateOfBirth, String consentVersion, Instant now) {
		this.id = Ids.newId();
		this.email = email;
		this.passwordHash = passwordHash;
		this.dateOfBirth = dateOfBirth;
		this.accountStatus = AccountStatus.ACTIVE;
		this.verificationStatus = VerificationStatus.UNVERIFIED;
		this.consentVersion = consentVersion;
		this.consentedAt = now;
		this.createdAt = now;
	}

	public void markVerified(Instant now) {
		this.verificationStatus = VerificationStatus.VERIFIED;
		this.verifiedAt = now;
	}

	public void markManualReview() {
		this.verificationStatus = VerificationStatus.MANUAL_REVIEW;
	}

	public boolean isVerified() {
		return verificationStatus == VerificationStatus.VERIFIED;
	}

	public boolean isActive() {
		return accountStatus == AccountStatus.ACTIVE;
	}

	public String getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public AccountStatus getAccountStatus() {
		return accountStatus;
	}

	public VerificationStatus getVerificationStatus() {
		return verificationStatus;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}

	public String getConsentVersion() {
		return consentVersion;
	}

	public Instant getConsentedAt() {
		return consentedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
