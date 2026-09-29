package oneday.notify;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, String> {

	List<Notice> findByUserIdOrderByCreatedAtDesc(String userId);

	void deleteByUserId(String userId);
}
