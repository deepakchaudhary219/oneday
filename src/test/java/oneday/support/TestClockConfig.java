package oneday.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** One controllable clock for the whole application context; tests only ever move it forward. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(Instant.now());
	}
}
