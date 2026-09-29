package oneday.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests can move forward to exercise the 24 h moment lifetime and the 48 h Reaction Window. */
public class MutableClock extends Clock {

	private final AtomicReference<Instant> now;

	private final ZoneId zone;

	public MutableClock(Instant start) {
		this(new AtomicReference<>(start), ZoneOffset.UTC);
	}

	private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
		this.now = now;
		this.zone = zone;
	}

	public void advance(Duration duration) {
		now.updateAndGet(i -> i.plus(duration));
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(now, zone);
	}

	@Override
	public Instant instant() {
		return now.get();
	}
}
