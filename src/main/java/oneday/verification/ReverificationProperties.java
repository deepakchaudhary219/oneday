package oneday.verification;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Periodic re-verification (blueprint §21.4).
 *
 * @param validity how long a liveness check stays valid
 * @param reminderBefore one in-app reminder this long before it lapses
 */
@ConfigurationProperties("oneday.reverification")
public record ReverificationProperties(Duration validity, Duration reminderBefore) {
}
