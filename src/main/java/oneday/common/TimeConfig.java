package oneday.common;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

	/**
	 * Single time source so the 24 h / 48 h windows are testable. It ticks in microseconds, the precision of
	 * the {@code DATETIME(6)} columns: a nanosecond instant would be rounded when stored, and a value written
	 * "now" could then compare as later than "now".
	 */
	@Bean
	Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000));
	}
}
