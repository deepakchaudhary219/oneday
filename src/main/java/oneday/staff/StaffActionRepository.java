package oneday.staff;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffActionRepository extends JpaRepository<StaffAction, String> {

	List<StaffAction> findAllByOrderByCreatedAtDesc(Pageable page);
}
