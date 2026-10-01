package oneday.pulsestatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.identity.UserGuard;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.realtime.RealtimeService;
import oneday.safety.BlockChecker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pulse Status (blueprint v2 §8): a mood, an emoji, an optional short note and an optional Spotify track, seen
 * only by friends (active connections) and gone after 24 hours, like Stories.
 *
 * <p>
 * Deliberately absent (docs/05-engagement-psychology.md): no "seen by" list, no push notification when a friend
 * updates, no streaks. Friends' open apps update live over the socket; everyone else sees it on their next look.
 */
@Service
public class PulseStatusService {

	static final Duration LIFETIME = Duration.ofHours(24);

	private static final int NOTE_MAX = 60;

	private static final int TITLE_MAX = 100;

	private final PulseStatusRepository statuses;

	private final ConnectionService connections;

	private final ProfileService profiles;

	private final UserGuard guard;

	private final BlockChecker blocks;

	private final RealtimeService realtime;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	public PulseStatusService(PulseStatusRepository statuses, ConnectionService connections, ProfileService profiles,
			UserGuard guard, BlockChecker blocks, RealtimeService realtime, RateLimiter rateLimiter, Clock clock) {
		this.statuses = statuses;
		this.connections = connections;
		this.profiles = profiles;
		this.guard = guard;
		this.blocks = blocks;
		this.realtime = realtime;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
	}

	@Transactional
	public MyStatusView set(String userId, SetStatus request) {
		guard.requireActive(userId);
		if (request.mood() == null) {
			throw ApiException.badRequest("MOOD_REQUIRED", "Pick a mood");
		}
		String emoji = Emoji.normalize(request.emoji())
			.orElseThrow(() -> ApiException.badRequest("INVALID_EMOJI", "Pick one to three emoji"));
		String note = clean(request.note(), NOTE_MAX, "NOTE_TOO_LONG", "Keep the note under 60 characters");
		String trackId = null;
		if (request.spotifyUrl() != null && !request.spotifyUrl().isBlank()) {
			trackId = Music.spotifyTrackId(request.spotifyUrl())
				.orElseThrow(() -> ApiException.badRequest("INVALID_MUSIC", "Share a Spotify track link"));
		}
		String title = clean(request.musicTitle(), TITLE_MAX, "MUSIC_TITLE_TOO_LONG", "That title is too long");
		if (!rateLimiter.tryAcquire("pulse-status:" + userId, 12, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("STATUS_TOO_FREQUENT", "You've updated your status a lot this hour");
		}
		Instant now = clock.instant();
		PulseStatus status = statuses.findById(userId).orElseGet(() -> new PulseStatus(userId));
		status.set(request.mood(), emoji, note, trackId, title, now, now.plus(LIFETIME));
		statuses.save(status);
		String firstName = profiles.require(userId).firstName();
		friendConnections(userId).forEach((friendId, connectionId) -> realtime.toUser(friendId, "pulse-status",
				FriendStatusView.of(status, connectionId, firstName)));
		return MyStatusView.of(status);
	}

	@Transactional
	public void clear(String userId) {
		guard.requireExisting(userId);
		if (statuses.existsById(userId)) {
			statuses.deleteById(userId);
			friendConnections(userId).forEach((friendId, connectionId) -> realtime.toUser(friendId,
					"pulse-status-cleared", new Cleared(connectionId)));
		}
	}

	@Transactional(readOnly = true)
	public Optional<MyStatusView> mine(String userId) {
		guard.requireActive(userId);
		Instant now = clock.instant();
		return statuses.findById(userId).filter(s -> s.isLive(now)).map(MyStatusView::of);
	}

	/** Friends' live statuses, newest first. Bounded by the number of connections. */
	@Transactional(readOnly = true)
	public List<FriendStatusView> friends(String viewerId) {
		guard.requireActive(viewerId);
		Map<String, String> friends = friendConnections(viewerId);
		if (friends.isEmpty()) {
			return List.of();
		}
		Set<String> reachable = guard.reachableAmong(friends.keySet());
		List<PulseStatus> live = statuses.findLive(reachable.isEmpty() ? List.of("-") : reachable, clock.instant());
		Map<String, String> names = new HashMap<>();
		live.forEach(s -> names.put(s.getUserId(), profiles.find(s.getUserId()).map(Profile::firstName).orElse("")));
		return live.stream()
			.map(s -> FriendStatusView.of(s, friends.get(s.getUserId()), names.get(s.getUserId())))
			.toList();
	}

	/** The person behind a status, for blocking or reporting it. Only friends can see one, so only they can. */
	@Transactional(readOnly = true)
	public Optional<String> ownerOf(String viewerId, String statusId) {
		return statuses.findByStatusId(statusId)
			.map(PulseStatus::getUserId)
			.filter(owner -> connections.areConnected(viewerId, owner));
	}

	@Transactional(readOnly = true)
	public Optional<MyStatusView> export(String userId) {
		return statuses.findById(userId).map(MyStatusView::of);
	}

	@Transactional
	public int purgeExpired() {
		return statuses.deleteExpired(clock.instant());
	}

	@Transactional
	public void forget(String userId) {
		statuses.deleteById(userId);
	}

	/** Friend id → the connection between them, excluding anyone blocked either way. */
	private Map<String, String> friendConnections(String userId) {
		Set<String> blocked = blocks.blockedEitherWay(userId);
		Map<String, String> friends = new HashMap<>();
		for (Connection c : connections.active(userId)) {
			String other = c.otherThan(userId);
			if (!blocked.contains(other)) {
				friends.put(other, c.getId());
			}
		}
		return friends;
	}

	private static String clean(String raw, int max, String code, String message) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.strip().replaceAll("\\s+", " ");
		if (value.codePointCount(0, value.length()) > max) {
			throw ApiException.badRequest(code, message);
		}
		return value;
	}

	public record SetStatus(Mood mood, String emoji, String note, String spotifyUrl, String musicTitle) {
	}

	public record MyStatusView(String id, Mood mood, String moodLabel, String emoji, String note, Music.View music,
			Instant setAt, Instant expiresAt) {

		static MyStatusView of(PulseStatus s) {
			return new MyStatusView(s.getId(), s.getMood(), s.getMood().label(), s.getEmoji(), s.getNote(),
					Music.view(s.getSpotifyTrackId(), s.getMusicTitle()), s.getSetAt(), s.getExpiresAt());
		}
	}

	/**
	 * A friend's status. {@code checkIn} is set when they're feeling low: the app offers a one-tap "thinking of
	 * you" message instead of a like.
	 */
	public record FriendStatusView(String statusId, String connectionId, String firstName, Mood mood,
			String moodLabel, String emoji, String note, Music.View music, Instant setAt, Instant expiresAt,
			boolean checkIn) {

		static FriendStatusView of(PulseStatus s, String connectionId, String firstName) {
			return new FriendStatusView(s.getId(), connectionId, firstName, s.getMood(), s.getMood().label(),
					s.getEmoji(), s.getNote(), Music.view(s.getSpotifyTrackId(), s.getMusicTitle()), s.getSetAt(),
					s.getExpiresAt(), s.getMood() == Mood.LOW);
		}
	}

	public record Cleared(String connectionId) {
	}
}
