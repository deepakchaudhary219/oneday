package oneday.plus;

/** The subscription provider port (Razorpay in India; others per region later). */
public interface PaymentGateway {

	String name();

	/** Creates a provider subscription and returns what the app needs to open the provider's checkout. */
	Checkout createSubscription(String localReference);

	void cancelAtCycleEnd(String providerSubscriptionId);

	/** Verifies a webhook's signature over the exact raw body. */
	boolean verifyWebhook(String rawBody, String signature);

	/**
	 * @param providerSubscriptionId the provider's id
	 * @param checkoutKey public key for the client SDK (never a secret)
	 * @param checkoutUrl hosted page, for clients without the SDK
	 */
	record Checkout(String providerSubscriptionId, String checkoutKey, String checkoutUrl) {
	}
}
