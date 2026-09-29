package oneday.connections;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectionRepository extends JpaRepository<Connection, String> {

	Optional<Connection> findByUserAAndUserB(String userA, String userB);

	@Query("select c from Connection c where (c.userA = :userId or c.userB = :userId) and c.state = :state "
			+ "order by c.createdAt desc")
	List<Connection> findByMemberAndState(@Param("userId") String userId, @Param("state") Connection.State state);

	@Query("select c from Connection c where c.userA = :userId or c.userB = :userId")
	List<Connection> findByMember(@Param("userId") String userId);

	@Query("select count(c) from Connection c where (c.userA = :userId or c.userB = :userId) "
			+ "and c.state = oneday.connections.Connection.State.ACTIVE and c.createdAt > :since")
	long countActiveSince(@Param("userId") String userId, @Param("since") Instant since);
}
