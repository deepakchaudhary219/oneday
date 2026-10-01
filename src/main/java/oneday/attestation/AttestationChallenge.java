package oneday.attestation;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "attestation_challenges")
class AttestationChallenge {

	@Id
	private String challenge;

	private Instant expiresAt;

	protected AttestationChallenge() {
	}

	AttestationChallenge(String challenge, Instant expiresAt) {
		this.challenge = challenge;
		this.expiresAt = expiresAt;
	}
}
