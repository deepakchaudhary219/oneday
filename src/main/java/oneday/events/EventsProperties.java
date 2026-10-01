package oneday.events;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox relay tuning: events per claim, how long a claim (lease) lasts, attempts before an event is parked
 * as DEAD, and how long delivered events and inbox rows are kept.
 */
@ConfigurationProperties("oneday.events")
public record EventsProperties(int batchSize, Duration lease, int maxAttempts, Duration retention) {
}
