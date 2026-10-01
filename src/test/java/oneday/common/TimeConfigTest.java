package oneday.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** The production clock: tests replace it with a controllable one, so it gets its own check. */
class TimeConfigTest {

	private final Clock clock = new TimeConfig().clock();

	@Test
	void instantsAreMicrosecondPreciseAndMillisWork() {
		Instant now = clock.instant();
		assertThat(now.getNano() % 1000).isZero();
		assertThat(Math.abs(clock.millis() - System.currentTimeMillis())).isLessThan(5_000);
		assertThat(clock.withZone(ZoneId.of("Asia/Kolkata")).instant()).isBetween(now, now.plusSeconds(5));
	}

	@Test
	void millisecondBasedComponentsRunOnIt() {
		InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
		assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isTrue();
		assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isFalse();
	}
}
