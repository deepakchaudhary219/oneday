package oneday.plus;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local development and tests: subscriptions are created locally and webhooks are signed exactly like Razorpay's
 * (HMAC-SHA256 of the body with {@code oneday.plus.razorpay.webhook-secret}), so the whole flow can be exercised
 * with a signed curl.
 */
@Component
@ConditionalOnProperty(name = "oneday.plus.provider", havingValue = "dev")
class DevPaymentGateway implements PaymentGateway {

	private final String secret;

	DevPaymentGateway(PlusProperties properties) {
		this.secret = properties.razorpay() == null || properties.razorpay().webhookSecret() == null
				? "dev-webhook-secret" : properties.razorpay().webhookSecret();
	}

	@Override
	public String name() {
		return "dev";
	}

	@Override
	public Checkout createSubscription(String localReference) {
		return new Checkout("dev_sub_" + UUID.randomUUID().toString().replace("-", ""), "dev_key", null);
	}

	@Override
	public void cancelAtCycleEnd(String providerSubscriptionId) {
	}

	@Override
	public boolean verifyWebhook(String rawBody, String signature) {
		return signature != null && signature.equals(sign(rawBody, secret));
	}

	static String sign(String body, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
