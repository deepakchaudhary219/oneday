package oneday.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import oneday.common.RedisRateLimiter;
import oneday.config.OneDayProperties;
import oneday.geo.RedisProbeBudget;
import oneday.support.MutableClock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Runs the Redis implementations against a real, throwaway {@code redis-server}. Skipped automatically
 * where Redis is not installed; CI images for multi-replica work should include it.
 */
@EnabledIf("redisServerInstalled")
class RedisStateStoreTest {

	private static Process server;

	private static LettuceConnectionFactory connections;

	private static StringRedisTemplate redis;

	private final MutableClock clock = new MutableClock(Instant.parse("2026-09-29T10:00:00Z"));

	private final OneDayProperties properties = new OneDayProperties(null, null, null,
			new OneDayProperties.Location("unit-test-location-secret-0123456789abcdef", Duration.ofSeconds(30), 1000,
					3),
			null, null, null);

	static boolean redisServerInstalled() {
		String path = System.getenv("PATH");
		if (path == null) {
			return false;
		}
		for (String dir : path.split(File.pathSeparator)) {
			if (new File(dir, "redis-server").canExecute()) {
				return true;
			}
		}
		return false;
	}

	@BeforeAll
	static void startRedis() throws Exception {
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		server = new ProcessBuilder("redis-server", "--port", String.valueOf(port), "--save", "", "--appendonly",
				"no")
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.start();
		connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", port));
		connections.afterPropertiesSet();
		connections.start();
		redis = new StringRedisTemplate(connections);
		redis.afterPropertiesSet();
		awaitReady();
	}

	@AfterAll
	static void stopRedis() {
		if (connections != null) {
			connections.destroy();
		}
		if (server != null) {
			server.destroy();
		}
	}

	@BeforeEach
	void flush() {
		redis.execute(connection -> {
			connection.serverCommands().flushAll();
			return null;
		}, true);
	}

	@Test
	void rateLimiterEnforcesASlidingWindowAcrossInstances() {
		RedisRateLimiter nodeA = new RedisRateLimiter(redis, clock);
		RedisRateLimiter nodeB = new RedisRateLimiter(redis, clock);
		assertThat(nodeA.tryAcquire("signup:1.2.3.4", 3, Duration.ofMinutes(10))).isTrue();
		assertThat(nodeB.tryAcquire("signup:1.2.3.4", 3, Duration.ofMinutes(10))).isTrue();
		assertThat(nodeA.tryAcquire("signup:1.2.3.4", 3, Duration.ofMinutes(10))).isTrue();
		// The limit is shared: the fourth attempt fails whichever replica serves it.
		assertThat(nodeB.tryAcquire("signup:1.2.3.4", 3, Duration.ofMinutes(10))).isFalse();
		assertThat(nodeA.tryAcquire("signup:5.6.7.8", 3, Duration.ofMinutes(10))).isTrue();

		clock.advance(Duration.ofMinutes(11));
		assertThat(nodeB.tryAcquire("signup:1.2.3.4", 3, Duration.ofMinutes(10))).isTrue();
	}

	@Test
	void rateLimiterKeysExpireWithTheWindow() {
		new RedisRateLimiter(redis, clock).tryAcquire("discover:u1", 5, Duration.ofMinutes(10));
		Long ttl = redis.getExpire("oneday:rl:discover:u1");
		assertThat(ttl).isBetween(1L, 600L);
	}

	@Test
	void probeBudgetCapsDistinctCellsAndAllowsRevisits() {
		RedisProbeBudget budget = new RedisProbeBudget(redis, clock, properties);
		assertThat(budget.tryVisit("u1", "tdr1wx")).isTrue();
		assertThat(budget.tryVisit("u1", "tdr1wy")).isTrue();
		assertThat(budget.tryVisit("u1", "tdr1wz")).isTrue();
		assertThat(budget.tryVisit("u1", "tdr1wx")).isTrue();
		assertThat(budget.tryVisit("u1", "tdr1x0")).isFalse();
		assertThat(budget.tryVisit("u2", "tdr1x0")).isTrue();

		clock.advance(Duration.ofMinutes(61));
		assertThat(budget.tryVisit("u1", "tdr1x0")).isTrue();
	}

	@Test
	void redisNeverHoldsAReadableLocationTrail() {
		RedisProbeBudget budget = new RedisProbeBudget(redis, clock, properties);
		budget.tryVisit("u1", "tdr1wx");
		budget.tryVisit("u1", "tdr1wy");
		Set<String> members = redis.opsForZSet().range("oneday:probe:u1", 0, -1);
		assertThat(members).hasSize(2).noneMatch(m -> m.contains("tdr1"));
		assertThat(redis.getExpire("oneday:probe:u1")).isBetween(1L, 3600L);

		budget.forget("u1");
		assertThat(redis.hasKey("oneday:probe:u1")).isFalse();
	}

	private static void awaitReady() throws InterruptedException, IOException {
		for (int i = 0; i < 50; i++) {
			try {
				if ("PONG".equals(redis.execute(connection -> connection.ping(), true))) {
					return;
				}
			}
			catch (RuntimeException notYet) {
				Thread.sleep(100);
			}
		}
		throw new IOException("redis-server did not start");
	}
}
