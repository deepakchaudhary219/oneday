package oneday.notify;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<Device, String> {

	Optional<Device> findByPushToken(String pushToken);

	List<Device> findByUserIdOrderByLastSeenAtDesc(String userId);

	@Query("select distinct d.userId from Device d")
	List<String> findUserIdsWithDevices();
}
