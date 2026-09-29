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

	/** Email or phone (E.164) identifies the account; phone-only accounts have no password. */
	@Column(unique = true)
	private String email;

	@Column(unique = true)
	private String phone;

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

	/** Set when erasure was requested but deferred by a safety hold. */
	private Instant erasureRequestedAt;

	/** The released email/phone, kept only while evidence is held (never used for login or lookup). */
	private String heldIdentifiers;

	protected User() {
	}

	private User(String email, String phone, String passwordHash, LocalDate dateOfBirth, String consentVersion,
			Instant now) {
		this.id = Ids.newId();
		this.email = email;
		this.phone = phone;
		this.passwordHash = passwordHash;
		this.dateOfBirth = dateOfBirth;
		this.accountStatus = AccountStatus.ACTIVE;
		this.verificationStatus = VerificationStatus.UNVERIFIED;
		this.consentVersion = consentVersion;
		this.consentedAt = now;
		this.createdAt = now;
	}

	public static User withEmail(String email, String passwordHash, LocalDate dateOfBirth, String consentVersion,
			Instant now) {
		return new User(email, null, passwordHash, dateOfBirth, consentVersion, now);
	}

	public static User withPhone(String phone, LocalDate dateOfBirth, String consentVersion, Instant now) {
		return new User(null, phone, null, dateOfBirth, consentVersion, now);
	}

	public void markVerified(Instant now) {
		this.verificationStatus = VerificationStatus.VERIFIED;
		this.verifiedAt = now;
	}

	public void markManualReview() {
		this.verificationStatus = VerificationStatus.MANUAL_REVIEW;
	}

	public void markRejected() {
		this.verificationStatus = VerificationStatus.REJECTED;
	}

	/** Lets the user take the liveness check again (e.g. a moderator asked for a clearer attempt). */
	public void resetVerification() {
		this.verificationStatus = VerificationStatus.UNVERIFIED;
	}

	/** No-op for an account awaiting erasure: it must keep looking deleted (the enforcement is on record). */
	public void suspend() {
		if (!isDeactivated()) {
			this.accountStatus = AccountStatus.SUSPENDED;
		}
	}

	public void reinstate() {
		this.accountStatus = AccountStatus.ACTIVE;
	}

	/**
	 * Deferred erasure: the account disappears now and its email/phone are released (so the person can
	 * even sign up again), while the records stay for the safety review.
	 */
	public void deactivateForErasure(Instant now) {
		this.accountStatus = AccountStatus.DEACTIVATED;
		this.erasureRequestedAt = now;
		this.heldIdentifiers = "email=" + (email == null ? "" : email) + ";phone=" + (phone == null ? "" : phone);
		this.email = null;
		this.phone = null;
		this.passwordHash = null;
	}

	public boolean isDeactivated() {
		return accountStatus == AccountStatus.DEACTIVATED;
	}

	public Instant getErasureRequestedAt() {
		return erasureRequestedAt;
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

	public String getPhone() {
		return phone;
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
