package oneday.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

	/** Single time source so the 24 h / 48 h windows are testable. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
