package oneday.dates;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Date Mode tunables (blueprint v2 §5.3).
 *
 * @param maxAdvance how far ahead a plan may be made
 * @param minDuration shortest plan
 * @param maxDuration longest plan: exact location is time-boxed by it
 * @param locationLead how long before the start exact-location sharing may begin
 * @param locationFreshness older positions are not shown (a stale dot is worse than none)
 * @param checkInAfter default "Going OK?" prompt time after the start
 * @param checkInGrace an unanswered prompt escalates to the trusted contact after this long
 * @param afterCareWindow after the end, SOS and the trusted contact's page stay available this long
 * @param escalationRetention an unresolved escalation keeps its evidence at most this long
 * @param emergencyNumber India's ERSS number
 * @param publicBaseUrl base of the link sent to trusted contacts
 * @param defaultCountryCode prefix for trusted contacts' national numbers
 */
@ConfigurationProperties("oneday.dates")
public record DateProperties(Duration maxAdvance, Duration minDuration, Duration maxDuration, Duration locationLead,
		Duration locationFreshness, Duration checkInAfter, Duration checkInGrace, Duration afterCareWindow,
		Duration escalationRetention, String emergencyNumber, String publicBaseUrl, String defaultCountryCode) {
}
