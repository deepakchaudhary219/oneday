package oneday.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

	/**
	 * Single time source so the 24 h / 48 h windows are testable. Instants are truncated to microseconds, the
	 * precision of the {@code DATETIME(6)} columns: a nanosecond instant would be rounded when stored, and a
	 * value written "now" could then compare as later than "now".
	 */
	@Bean
	Clock clock() {
		return new MicrosecondClock(Clock.systemUTC());
	}

	/**
	 * Not {@code Clock.tick(base, 1µs)}: the JDK's tick clock computes {@code millis()} modulo
	 * {@code tickNanos / 1_000_000}, which is zero for sub-millisecond ticks, so every {@code millis()} call threw.
	 */
	static final class MicrosecondClock extends Clock {

		private final Clock base;

		MicrosecondClock(Clock base) {
			this.base = base;
		}

		@Override
		public Instant instant() {
			return base.instant().truncatedTo(ChronoUnit.MICROS);
		}

		@Override
		public long millis() {
			return base.millis();
		}

		@Override
		public ZoneId getZone() {
			return base.getZone();
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return zone.equals(base.getZone()) ? this : new MicrosecondClock(base.withZone(zone));
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof MicrosecondClock clock && clock.base.equals(base);
		}

		@Override
		public int hashCode() {
			return base.hashCode() ^ 0x6d6963;
		}

		@Override
		public String toString() {
			return "MicrosecondClock[" + base + "]";
		}
	}
}
