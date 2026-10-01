package oneday.common;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Sliding window over a Redis sorted set, evaluated atomically in one Lua script so concurrent replicas
 * can never jointly exceed the limit. Keys expire with the window, so idle users cost nothing.
 */
@Component
@ConditionalOnProperty(name = "oneday.state.store", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

	static final String KEY_PREFIX = "oneday:rl:";

	private static final RedisScript<Long> SLIDING_WINDOW = RedisScript.of("""
			local key = KEYS[1]
			local now = tonumber(ARGV[1])
			local window = tonumber(ARGV[2])
			local limit = tonumber(ARGV[3])
			redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
			if redis.call('ZCARD', key) >= limit then
			  return 0
			end
			redis.call('ZADD', key, now, ARGV[4])
			redis.call('PEXPIRE', key, window)
			return 1
			""", Long.class);

	private final StringRedisTemplate redis;

	private final Clock clock;

	public RedisRateLimiter(StringRedisTemplate redis, Clock clock) {
		this.redis = redis;
		this.clock = clock;
	}

	@Override
	public boolean tryAcquire(String key, int limit, Duration window) {
		Long allowed = redis.execute(SLIDING_WINDOW, List.of(KEY_PREFIX + key), String.valueOf(clock.millis()),
				String.valueOf(window.toMillis()), String.valueOf(limit), UUID.randomUUID().toString());
		return allowed != null && allowed == 1L;
	}
}
