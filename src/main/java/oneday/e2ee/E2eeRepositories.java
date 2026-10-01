package oneday.e2ee;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface E2eeDeviceRepository extends JpaRepository<E2eeDevice, E2eeDevice.Key> {

	@Query("select d from E2eeDevice d where d.key.userId = :userId order by d.key.deviceId")
	List<E2eeDevice> findByUser(@Param("userId") String userId);

	@Query("select coalesce(max(d.key.deviceId), 0) from E2eeDevice d where d.key.userId = :userId")
	int maxDeviceId(@Param("userId") String userId);

	@Modifying
	@Query("delete from E2eeDevice d where d.key.userId = :userId")
	void deleteByUser(@Param("userId") String userId);
}

interface OneTimePreKeyRepository extends JpaRepository<OneTimePreKey, OneTimePreKey.Key> {

	@Query("select k from OneTimePreKey k where k.key.userId = :userId and k.key.deviceId = :deviceId order by k.key.keyId")
	List<OneTimePreKey> findAvailable(@Param("userId") String userId, @Param("deviceId") int deviceId, Pageable page);

	@Query("select count(k) from OneTimePreKey k where k.key.userId = :userId and k.key.deviceId = :deviceId")
	long countFor(@Param("userId") String userId, @Param("deviceId") int deviceId);

	/** 1 when this caller won the prekey, 0 when someone else claimed it first. */
	@Modifying
	@Query("delete from OneTimePreKey k where k.key = :key")
	int claim(@Param("key") OneTimePreKey.Key key);

	@Modifying
	@Query("delete from OneTimePreKey k where k.key.userId = :userId and k.key.deviceId = :deviceId")
	void deleteForDevice(@Param("userId") String userId, @Param("deviceId") int deviceId);

	@Modifying
	@Query("delete from OneTimePreKey k where k.key.userId = :userId")
	void deleteByUser(@Param("userId") String userId);
}

interface EnvelopeRepository extends JpaRepository<Envelope, String> {

	@Query("select e from Envelope e where e.recipientUserId = :userId and e.recipientDeviceId = :deviceId "
			+ "order by e.createdAt, e.id")
	List<Envelope> inbox(@Param("userId") String userId, @Param("deviceId") int deviceId, Pageable page);

	@Modifying
	@Query("delete from Envelope e where e.recipientUserId = :userId and e.recipientDeviceId = :deviceId and e.id in :ids")
	int acknowledge(@Param("userId") String userId, @Param("deviceId") int deviceId, @Param("ids") Collection<String> ids);

	@Modifying
	@Query("delete from Envelope e where e.recipientUserId = :userId and e.recipientDeviceId = :deviceId")
	void deleteForDevice(@Param("userId") String userId, @Param("deviceId") int deviceId);

	@Modifying
	@Query("delete from Envelope e where e.conversationId in :conversationIds")
	void deleteForConversations(@Param("conversationIds") Collection<String> conversationIds);

	@Modifying
	@Query("delete from Envelope e where e.recipientUserId = :userId or e.senderUserId = :userId")
	void deleteInvolving(@Param("userId") String userId);

	@Modifying
	@Query("delete from Envelope e where e.createdAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
