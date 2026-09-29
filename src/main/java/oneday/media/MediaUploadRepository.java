package oneday.media;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, String> {

	Optional<MediaUpload> findByObjectKeyAndOwnerId(String objectKey, String ownerId);

	Optional<MediaUpload> findByObjectKey(String objectKey);

	List<MediaUpload> findByOwnerId(String ownerId);

	List<MediaUpload> findByStatusAndUpdatedAtBefore(MediaUpload.Status status, Instant cutoff);
}
