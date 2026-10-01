package oneday.geo;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import oneday.config.OneDayProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Shared probe budget for multi-replica deployments. Cells are stored as keyed HMACs, never as geohashes,
 * so someone reading Redis cannot reconstruct where a user has been; entries expire after an hour.
 */
@Component
@ConditionalOnProperty(name = "oneday.state.store", havingValue = "redis")
public class RedisProbeBudget implements ProbeBudget {

	static final String KEY_PREFIX = "oneday:probe:";

	private static final Duration WINDOW = Duration.ofHours(1);

	private static final RedisScript<Long> VISIT = RedisScript.of("""
			local key = KEYS[1]
			local now = tonumber(ARGV[1])
			local window = tonumber(ARGV[2])
			local max = tonumber(ARGV[3])
			redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
			if redis.call('ZSCORE', key, ARGV[4]) then
			  return 1
			end
			if redis.call('ZCARD', key) >= max then
			  return 0
			end
			redis.call('ZADD', key, now, ARGV[4])
			redis.call('PEXPIRE', key, window)
			return 1
			""", Long.class);

	private final StringRedisTemplate redis;

	private final Clock clock;

	private final int maxDistinctCells;

	private final SecretKeySpec key;

	public RedisProbeBudget(StringRedisTemplate redis, Clock clock, OneDayProperties properties) {
		this.redis = redis;
		this.clock = clock;
		this.maxDistinctCells = properties.location().maxDistinctCellsPerHour();
		String secret = properties.location().jitterSecret();
		byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("oneday.location.jitter-secret must be set to at least 32 bytes");
		}
		this.key = new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Override
	public boolean tryVisit(String userId, String cell) {
		Long allowed = redis.execute(VISIT, List.of(KEY_PREFIX + userId), String.valueOf(clock.millis()),
				String.valueOf(WINDOW.toMillis()), String.valueOf(maxDistinctCells), opaque(cell));
		return allowed != null && allowed == 1L;
	}

	@Override
	public void forget(String userId) {
		redis.delete(KEY_PREFIX + userId);
	}

	private String opaque(String cell) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			byte[] digest = mac.doFinal(("probe|" + cell).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 12);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 unavailable", ex);
		}
	}
}
