package oneday.prompts;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPromptRepository extends JpaRepository<DailyPrompt, String> {

	Optional<DailyPrompt> findFirstByPromptDateAndHomeRegionOrderByCreatedAtDesc(LocalDate date, String homeRegion);

	Optional<DailyPrompt> findFirstByPromptDateAndHomeRegionIsNullOrderByCreatedAtDesc(LocalDate date);

	List<DailyPrompt> findByPromptDateGreaterThanEqualOrderByPromptDateAsc(LocalDate from);
}
