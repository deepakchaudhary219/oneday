package oneday.attestation;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-issued, single-use challenges that the app folds into what the device signs, so an attestation or
 * assertion can't be replayed or moved to another action.
 */
@Component
public class AttestationChallenges {

	static final Duration LIFETIME = Duration.ofMinutes(5);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AttestationChallengeRepository challenges;

	private final Clock clock;

	AttestationChallenges(AttestationChallengeRepository challenges, Clock clock) {
		this.challenges = challenges;
		this.clock = clock;
	}

	@Transactional
	public Issued issue() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiresAt = clock.instant().plus(LIFETIME);
		challenges.save(new AttestationChallenge(challenge, expiresAt));
		return new Issued(challenge, expiresAt);
	}

	/**
	 * Uses up a challenge. True only for the first caller of a live challenge. Committed on its own, so a
	 * failed verification later in the request can't put it back.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean consume(String challenge) {
		return challenge != null && challenge.length() <= 64 && challenges.consume(challenge, clock.instant()) == 1;
	}

	@Scheduled(fixedDelayString = "${oneday.attestation.challenge-sweep:PT10M}", initialDelayString = "PT3M")
	@Transactional
	public void purgeExpired() {
		challenges.deleteExpired(clock.instant());
	}

	public record Issued(String challenge, Instant expiresAt) {
	}
}

interface AttestationChallengeRepository extends JpaRepository<AttestationChallenge, String> {

	@Modifying
	@Query("delete from AttestationChallenge c where c.challenge = :challenge and c.expiresAt > :now")
	int consume(@Param("challenge") String challenge, @Param("now") Instant now);

	@Modifying
	@Query("delete from AttestationChallenge c where c.expiresAt <= :now")
	int deleteExpired(@Param("now") Instant now);
}
