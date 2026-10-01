package oneday.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;

import oneday.common.InMemoryRateLimiter;
import oneday.common.RateLimiter;
import oneday.common.RedisRateLimiter;
import oneday.config.OneDayProperties;
import oneday.geo.InMemoryProbeBudget;
import oneday.geo.ProbeBudget;
import oneday.geo.RedisProbeBudget;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Exactly one implementation of each port is active, chosen by {@code oneday.state.store}. */
class StateStoreWiringTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(Clock.class, Clock::systemUTC)
		.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
		.withBean(OneDayProperties.class,
				() -> new OneDayProperties(null, null, null,
						new OneDayProperties.Location("wiring-test-location-secret-0123456789abcdef",
								Duration.ofSeconds(30), 1000, 12),
						null, null, null))
		.withUserConfiguration(InMemoryRateLimiter.class, RedisRateLimiter.class, InMemoryProbeBudget.class,
				RedisProbeBudget.class);

	@Test
	void memoryIsTheDefault() {
		runner.run(context -> {
			assertThat(context).getBean(RateLimiter.class).isInstanceOf(InMemoryRateLimiter.class);
			assertThat(context).getBean(ProbeBudget.class).isInstanceOf(InMemoryProbeBudget.class);
		});
	}

	@Test
	void redisModeSwapsBothImplementations() {
		runner.withPropertyValues("oneday.state.store=redis").run(context -> {
			assertThat(context).getBean(RateLimiter.class).isInstanceOf(RedisRateLimiter.class);
			assertThat(context).getBean(ProbeBudget.class).isInstanceOf(RedisProbeBudget.class);
		});
	}
}
