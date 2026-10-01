package oneday.plus;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OneDay Plus: what it adds (never safety) and how it is billed.
 *
 * @param provider {@code razorpay}, {@code dev} or {@code none}
 * @param priceLabel shown to people as is, e.g. "₹149/month"
 * @param grace Plus keeps working this long past a missed renewal while the provider retries
 */
@ConfigurationProperties("oneday.plus")
public record PlusProperties(String provider, String priceLabel, Duration grace, int freeMaxRadiusKm,
		int plusMaxRadiusKm, int freeMaxOpenPlans, int plusMaxOpenPlans, Razorpay razorpay) {

	/** Razorpay keys (server side only), the subscription plan id and the webhook signing secret. */
	public record Razorpay(String keyId, String keySecret, String planId, String webhookSecret, String endpoint) {
	}
}
