package oneday.attestation;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AppAttestKeyRepository extends JpaRepository<AppAttestKey, String> {

	/** Moves the counter forward only; 0 rows means a replayed (or cloned) assertion lost the race. */
	@Modifying
	@Query("update AppAttestKey k set k.signCount = :count, k.lastUsedAt = :now where k.keyId = :keyId and k.signCount < :count")
	int advance(@Param("keyId") String keyId, @Param("count") long count, @Param("now") Instant now);
}
