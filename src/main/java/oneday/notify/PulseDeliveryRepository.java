package oneday.notify;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PulseDeliveryRepository extends JpaRepository<PulseDelivery, PulseDelivery.Key> {

	@Modifying
	@Query("delete from PulseDelivery d where d.key.userId = :userId")
	void deleteByUserId(@Param("userId") String userId);
}
