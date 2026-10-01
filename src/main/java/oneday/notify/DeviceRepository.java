package oneday.notify;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, String> {

	Optional<Device> findByPushToken(String pushToken);

	List<Device> findByUserIdOrderByLastSeenAtDesc(String userId);

	@Query("select distinct d.userId from Device d")
	List<String> findUserIdsWithDevices();

	/** People with a device whose chosen pulse hour, in this zone, is {@code hour} (index: time_zone, pulse_hour). */
	@Query("select distinct d.userId from Device d, oneday.profile.Profile p where p.userId = d.userId "
			+ "and p.timeZone = :zone and p.pulseHour = :hour")
	List<String> findUserIdsDueInZone(@Param("zone") String zone, @Param("hour") int hour);
}
