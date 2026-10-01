package oneday.platform;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Idempotency records in the shared database (so every replica sees them). The primary key
 * (user, key) is the lock: the first insert wins, concurrent duplicates see the in-progress row.
 */
@Component
public class IdempotencyStore {

	static final Duration RETENTION = Duration.ofHours(24);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public IdempotencyStore(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Claims the key, or returns what is already stored under it. */
	Optional<Stored> claim(String userId, String key, String requestHash) {
		try {
			jdbc.update("insert into idempotency_keys (user_id, idem_key, request_hash, created_at) values (?, ?, ?, ?)",
					userId, key, requestHash, Timestamp.from(clock.instant()));
			return Optional.empty();
		}
		catch (DuplicateKeyException taken) {
			List<Stored> rows = jdbc.query(
					"select request_hash, status, content_type, body from idempotency_keys where user_id = ? and idem_key = ?",
					(rs, i) -> new Stored(rs.getString(1), (Integer) rs.getObject(2), rs.getString(3), rs.getString(4)),
					userId, key);
			return rows.isEmpty() ? claim(userId, key, requestHash) : Optional.of(rows.get(0));
		}
	}

	void complete(String userId, String key, int status, String contentType, String body) {
		jdbc.update("update idempotency_keys set status = ?, content_type = ?, body = ? where user_id = ? and idem_key = ?",
				status, contentType, body, userId, key);
	}

	void release(String userId, String key) {
		jdbc.update("delete from idempotency_keys where user_id = ? and idem_key = ?", userId, key);
	}

	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT30M")
	public void purge() {
		jdbc.update("delete from idempotency_keys where created_at < ?", Timestamp.from(clock.instant().minus(RETENTION)));
	}

	/** Erasure: the stored responses may contain the person's data. */
	public void forget(String userId) {
		jdbc.update("delete from idempotency_keys where user_id = ?", userId);
	}

	record Stored(String requestHash, Integer status, String contentType, String body) {
	}
}
