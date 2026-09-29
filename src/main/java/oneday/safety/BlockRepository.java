package oneday.safety;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, String> {

	boolean existsByBlockerIdAndBlockedId(String blockerId, String blockedId);

	@Query("select case when count(b) > 0 then true else false end from Block b "
			+ "where (b.blockerId = :a and b.blockedId = :b) or (b.blockerId = :b and b.blockedId = :a)")
	boolean existsEitherWay(@Param("a") String a, @Param("b") String b);

	List<Block> findByBlockerIdOrBlockedId(String blockerId, String blockedId);

	List<Block> findByBlockerId(String blockerId);

	@Modifying
	@Query("delete from Block b where b.blockerId = :userId or b.blockedId = :userId")
	void deleteInvolving(@Param("userId") String userId);
}
