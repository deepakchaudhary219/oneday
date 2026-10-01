package oneday.profile;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfileRepository extends JpaRepository<Profile, String> {

	@Query("select distinct p.timeZone from Profile p")
	List<String> findTimeZonesInUse();
}
