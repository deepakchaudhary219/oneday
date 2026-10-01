package oneday.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param defaultCountryCode prefix added to bare 10-digit national numbers (India: +91)
 * @param perPhoneLimit codes that may be requested for one number per {@code perPhoneWindow}
 */
@ConfigurationProperties("oneday.otp")
public record OtpProperties(
		Duration ttl,
		int maxAttempts,
		int perPhoneLimit,
		Duration perPhoneWindow,
		int perIpPerHour,
		String defaultCountryCode) {
}
