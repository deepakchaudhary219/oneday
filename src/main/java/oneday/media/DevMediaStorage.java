package oneday.media;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fake storage for local development and tests: returns non-routable URLs and records deletions. */
@Component
@ConditionalOnProperty(name = "oneday.media.provider", havingValue = "dev")
public class DevMediaStorage implements MediaStorage {

	static final String BASE = "https://media.dev.invalid/";

	private final Clock clock;

	private final List<String> deleted = new CopyOnWriteArrayList<>();

	public DevMediaStorage(Clock clock) {
		this.clock = clock;
	}

	@Override
	public PresignedUpload presignUpload(String key, String contentType, long contentLength, Duration ttl) {
		return new PresignedUpload(BASE + key + "?dev-signature=upload", Map.of("content-type", contentType),
				clock.instant().plus(ttl));
	}

	@Override
	public String presignView(String key, Duration ttl) {
		return BASE + key + "?dev-signature=view";
	}

	@Override
	public void delete(Collection<String> keys) {
		deleted.addAll(keys);
	}

	public List<String> deletedKeys() {
		return List.copyOf(deleted);
	}
}
