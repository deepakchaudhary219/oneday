package oneday.safety;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param evidenceRetention how long records of an enforced (suspended) account are kept after action,
 * even if the holder asks for erasure. India's IT Rules 2021, Rule 3(1)(g), require 180 days.
 */
@ConfigurationProperties("oneday.safety")
public record SafetyProperties(Duration evidenceRetention) {
}
