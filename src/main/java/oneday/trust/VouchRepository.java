package oneday.trust;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VouchRepository extends JpaRepository<Vouch, Vouch.Key> {

	long countByKeyVoucherIdAndCreatedAtAfter(String voucherId, Instant since);

	List<Vouch> findByKeyVoucheeIdIn(Collection<String> voucheeIds);

	List<Vouch> findByKeyVoucherId(String voucherId);

	long countByKeyVoucheeId(String voucheeId);

	@Modifying
	@Query("delete from Vouch v where (v.key.voucherId = :a and v.key.voucheeId = :b) "
			+ "or (v.key.voucherId = :b and v.key.voucheeId = :a)")
	void deleteBetween(@Param("a") String a, @Param("b") String b);

	@Modifying
	@Query("delete from Vouch v where v.key.voucherId = :userId or v.key.voucheeId = :userId")
	void deleteInvolving(@Param("userId") String userId);
}
