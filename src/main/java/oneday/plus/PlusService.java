package oneday.plus;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;

import oneday.common.ApiException;
import oneday.common.Ids;
import oneday.identity.UserGuard;
import oneday.platform.Hashes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OneDay Plus (blueprint v2 §7.5): honest monetisation. Plus adds convenience only. The things we never sell are
 * listed in the response itself, so the promise is visible in the product, not just in a policy page.
 */
@Service
public class PlusService {

	static final List<String> BENEFITS = List.of("A wider discovery radius (up to 15 km)",
			"Host more Plans at the same time", "Support the people keeping OneDay safe");

	static final List<String> NEVER_SOLD = List.of("Safety features: Date Mode, SOS and trusted contacts are free for everyone",
			"Visibility boosts", "Seeing who signalled you", "Undoing a pass", "Read receipts",
			"Extra signals beyond the daily budget");

	private static final Logger log = LoggerFactory.getLogger(PlusService.class);

	private final SubscriptionRepository subscriptions;

	private final ObjectProvider<PaymentGateway> gateway;

	private final Entitlements entitlements;

	private final PlusProperties settings;

	private final UserGuard guard;

	private final JdbcTemplate jdbc;

	private final JsonMapper json;

	private final MeterRegistry metrics;

	private final Clock clock;

	public PlusService(SubscriptionRepository subscriptions, ObjectProvider<PaymentGateway> gateway,
			Entitlements entitlements, PlusProperties settings, UserGuard guard, JdbcTemplate jdbc, JsonMapper json,
			MeterRegistry metrics, Clock clock) {
		this.subscriptions = subscriptions;
		this.gateway = gateway;
		this.entitlements = entitlements;
		this.settings = settings;
		this.guard = guard;
		this.jdbc = jdbc;
		this.json = json;
		this.metrics = metrics;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PlusView status(String userId) {
		guard.requireExisting(userId);
		Optional<Subscription> latest = subscriptions.findByUserIdOrderByCreatedAtDesc(userId).stream().findFirst();
		boolean plus = entitlements.isPlus(userId);
		return new PlusView(plus, latest.map(s -> s.getStatus().name()).orElse(null),
				plus ? latest.map(Subscription::getCurrentEnd).orElse(null) : null,
				latest.map(Subscription::isCancelAtCycleEnd).orElse(false), settings.priceLabel(),
				gateway.getIfAvailable() != null, BENEFITS, NEVER_SOLD);
	}

	@Transactional
	public CheckoutView subscribe(String userId) {
		guard.requireActive(userId);
		PaymentGateway provider = gateway.getIfAvailable();
		if (provider == null) {
			throw ApiException.unavailable("PLUS_UNAVAILABLE", "Plus isn't available yet");
		}
		if (entitlements.isPlus(userId) && subscriptions.findByUserIdOrderByCreatedAtDesc(userId)
			.stream()
			.anyMatch(s -> s.getStatus() == Subscription.Status.ACTIVE && !s.isCancelAtCycleEnd())) {
			throw ApiException.conflict("ALREADY_PLUS", "You already have Plus");
		}
		PaymentGateway.Checkout checkout = provider.createSubscription(Ids.newId());
		Subscription subscription = subscriptions
			.save(new Subscription(userId, provider.name(), checkout.providerSubscriptionId(), clock.instant()));
		return new CheckoutView(subscription.getProviderSubscriptionId(), checkout.checkoutKey(), checkout.checkoutUrl(),
				settings.priceLabel());
	}

	/** Cancels at the end of the paid period: what was paid for keeps working; nothing more is charged. */
	@Transactional
	public PlusView cancel(String userId) {
		Subscription active = subscriptions.findByUserIdOrderByCreatedAtDesc(userId)
			.stream()
			.filter(s -> s.getStatus() == Subscription.Status.ACTIVE && !s.isCancelAtCycleEnd())
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("Active subscription"));
		PaymentGateway provider = gateway.getIfAvailable();
		if (provider != null) {
			provider.cancelAtCycleEnd(active.getProviderSubscriptionId());
		}
		active.cancelAtCycleEnd(clock.instant());
		return status(userId);
	}

	/**
	 * Applies a provider webhook. Unsigned or wrongly signed bodies are refused; a delivery already applied
	 * (providers retry) is acknowledged without doing anything again.
	 */
	@Transactional
	public void webhook(String rawBody, String signature, String eventId) {
		PaymentGateway provider = gateway.getIfAvailable();
		if (provider == null || !provider.verifyWebhook(rawBody, signature)) {
			metrics.counter("oneday.plus.webhooks", "outcome", "bad_signature").increment();
			throw ApiException.unauthorized("BAD_SIGNATURE", "Webhook signature does not match");
		}
		String id = eventId != null && !eventId.isBlank() ? eventId.trim()
				: "sha256:" + Hashes.sha256(rawBody);
		try {
			jdbc.update("insert into payment_webhook_events (id, received_at) values (?, ?)", id,
					Timestamp.from(clock.instant()));
		}
		catch (DataIntegrityViolationException duplicate) {
			metrics.counter("oneday.plus.webhooks", "outcome", "duplicate").increment();
			return;
		}
		JsonNode root = json.readTree(rawBody);
		String event = root.path("event").asText();
		JsonNode entity = root.path("payload").path("subscription").path("entity");
		String providerId = entity.path("id").asText(null);
		if (providerId == null) {
			return;
		}
		Optional<Subscription> found = subscriptions.findByProviderAndProviderSubscriptionId(provider.name(), providerId);
		if (found.isEmpty()) {
			log.warn("Webhook {} for an unknown subscription", event);
			return;
		}
		Instant currentEnd = entity.hasNonNull("current_end") ? Instant.ofEpochSecond(entity.get("current_end").asLong())
				: null;
		Subscription.Status status = switch (event) {
			case "subscription.activated", "subscription.charged", "subscription.resumed" -> Subscription.Status.ACTIVE;
			case "subscription.halted" -> Subscription.Status.HALTED;
			case "subscription.cancelled" -> Subscription.Status.CANCELLED;
			case "subscription.completed" -> Subscription.Status.COMPLETED;
			default -> null;
		};
		if (status != null) {
			found.get().update(status, currentEnd, clock.instant());
			metrics.counter("oneday.plus.webhooks", "outcome", event).increment();
		}
	}

	/** Erasure: payment records are kept (tax law) but detached from the person. */
	@Transactional
	public void forget(String userId) {
		subscriptions.findByUserIdOrderByCreatedAtDesc(userId).forEach(s -> s.detach(clock.instant()));
	}

	/**
	 * @param renewsOrEndsAt end of the paid period, while Plus is on
	 * @param available whether Plus can be bought in this deployment
	 */
	public record PlusView(boolean plus, String status, Instant renewsOrEndsAt, boolean cancelAtPeriodEnd,
			String price, boolean available, List<String> benefits, List<String> neverSold) {
	}

	/** What the app needs to open the provider's checkout (public key only). */
	public record CheckoutView(String subscriptionId, String checkoutKey, String checkoutUrl, String price) {
	}
}
