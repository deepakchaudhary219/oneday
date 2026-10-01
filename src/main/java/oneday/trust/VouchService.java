package oneday.trust;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import oneday.chat.ChatService;
import oneday.chat.Conversation;
import oneday.common.ApiException;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.identity.UserGuard;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trusted Vouch (blueprint v2 §11, "trust transfer"). Liveness proves a real face; a vouch adds that a real
 * person who has actually talked with you stands behind you.
 *
 * <ul>
 * <li>Only a verified Connection of at least {@link #MIN_CONNECTION_AGE} who has had a two-way conversation
 * with you on at least {@link #MIN_MUTUAL_DAYS} different days can vouch.</li>
 * <li>A small budget ({@link #PER_MONTH} a month) keeps vouches meaningful and resists vouch rings.</li>
 * <li>Strangers see only a capped count ("Vouched by 2"); nobody ever learns who vouched, or who withdrew.</li>
 * <li>Vouches from suspended, unverified or erased accounts stop counting at once; a block removes them.</li>
 * </ul>
 */
@Service
public class VouchService {

	static final Duration MIN_CONNECTION_AGE = Duration.ofDays(14);

	static final int MIN_MUTUAL_DAYS = 2;

	static final int PER_MONTH = 5;

	static final int DISPLAY_CAP = 5;

	private final VouchRepository vouches;

	private final ConnectionService connections;

	private final ChatService chat;

	private final ProfileService profiles;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final Clock clock;

	public VouchService(VouchRepository vouches, ConnectionService connections, ChatService chat,
			ProfileService profiles, BlockChecker blocks, UserGuard guard, Clock clock) {
		this.vouches = vouches;
		this.connections = connections;
		this.chat = chat;
		this.profiles = profiles;
		this.blocks = blocks;
		this.guard = guard;
		this.clock = clock;
	}

	@Transactional
	public VouchView vouch(String userId, String connectionId) {
		guard.requireContactAllowed(userId);
		Connection connection = connections.requireMember(connectionId, userId);
		String other = connection.otherThan(userId);
		Instant now = clock.instant();
		if (!connection.isActive() || blocks.isBlockedEitherWay(userId, other)
				|| guard.reachableAmong(List.of(other)).isEmpty()) {
			throw ApiException.conflict("CONNECTION_INACTIVE", "This connection is no longer active");
		}
		if (vouches.existsById(new Vouch.Key(userId, other))) {
			return VouchView.of(true, "You already vouch for them.");
		}
		if (connection.getCreatedAt().isAfter(now.minus(MIN_CONNECTION_AGE))) {
			throw ApiException.conflict("TOO_SOON", "Vouch once you've known them for at least two weeks");
		}
		String conversationId = chat.forConnection(connectionId).map(Conversation::getId).orElse(null);
		ZoneId zone = ZoneId.of(profiles.require(userId).getTimeZone());
		int mutualDays = conversationId == null ? 0
				: chat.rhythm(conversationId, now.minus(Duration.ofDays(90)), zone).mutualDays();
		if (mutualDays < MIN_MUTUAL_DAYS) {
			throw ApiException.conflict("NOT_ENOUGH_CONTACT", "Vouch for people you've really talked with");
		}
		if (vouches.countByKeyVoucherIdAndCreatedAtAfter(userId, now.minus(Duration.ofDays(30))) >= PER_MONTH) {
			throw ApiException.tooManyRequests("VOUCH_BUDGET", "You've vouched for five people this month");
		}
		vouches.save(new Vouch(userId, other, now));
		return VouchView.of(true, "Thanks. Your vouch helps them be trusted, and it's never shown as yours.");
	}

	/** Withdrawing is silent: the count simply goes down. */
	@Transactional
	public VouchView withdraw(String userId, String connectionId) {
		Connection connection = connections.requireMember(connectionId, userId);
		vouches.findById(new Vouch.Key(userId, connection.otherThan(userId))).ifPresent(vouches::delete);
		return VouchView.of(false, "Your vouch is withdrawn.");
	}

	/** Capped, display-ready vouch counts for the given people ("5+" at most); people without vouches are absent. */
	@Transactional(readOnly = true)
	public Map<String, String> countsFor(Collection<String> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		List<Vouch> found = vouches.findByKeyVoucheeIdIn(userIds);
		Set<String> trustedVouchers = guard.reachableAmong(found.stream().map(Vouch::getVoucherId).toList());
		Map<String, Integer> counts = new HashMap<>();
		for (Vouch v : found) {
			if (trustedVouchers.contains(v.getVoucherId())) {
				counts.merge(v.getVoucheeId(), 1, Integer::sum);
			}
		}
		Map<String, String> labels = new HashMap<>();
		counts.forEach((id, n) -> labels.put(id, n > DISPLAY_CAP ? DISPLAY_CAP + "+" : String.valueOf(n)));
		return labels;
	}

	@Transactional(readOnly = true)
	public MyVouches mine(String userId) {
		guard.requireExisting(userId);
		String received = countsFor(List.of(userId)).getOrDefault(userId, "0");
		List<String> given = vouches.findByKeyVoucherId(userId)
			.stream()
			.map(v -> profiles.find(v.getVoucheeId()).map(Profile::firstName).orElse(null))
			.filter(name -> name != null)
			.toList();
		return new MyVouches(received, given);
	}

	@Transactional
	public void removeBetween(String a, String b) {
		vouches.deleteBetween(a, b);
	}

	@Transactional
	public void forget(String userId) {
		vouches.deleteInvolving(userId);
	}

	public record VouchView(boolean youVouch, String message) {

		static VouchView of(boolean vouch, String message) {
			return new VouchView(vouch, message);
		}
	}

	/** {@code received} is capped; {@code given} lists first names of the people you vouch for. */
	public record MyVouches(String received, List<String> given) {
	}
}
