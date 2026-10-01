package oneday.live;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import oneday.common.ApiException;
import oneday.connections.ConnectionService;
import oneday.identity.UserGuard;
import oneday.live.LiveTokens.Access;
import oneday.platform.Hashes;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.realtime.RealtimeService;
import oneday.safety.BlockChecker;
import oneday.threads.ThreadService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Circle Live (v3; docs/07-v3-features.md): go live to your Connections, or to one Collaborative Thread you're
 * in. The server decides who may watch and issues SFU tokens; video flows through the SFU, never the API.
 *
 * <ul>
 * <li>Only the audience can see or join. Blocks apply both ways; joining is re-checked every 10 minutes (the
 * viewer token's lifetime).</li>
 * <li>Up to 60 minutes; one live at a time per person. Never recorded.</li>
 * <li>Audiences learn about it in their open app (socket) and in {@code GET /live}; there is no push, since a
 * friend going live is not urgent.</li>
 * <li>The viewer count is the host's alone.</li>
 * </ul>
 */
@Service
public class LiveService {

	static final Duration MAX_DURATION = Duration.ofMinutes(60);

	static final Duration RETENTION = Duration.ofDays(30);

	private final LiveSessionRepository sessions;

	private final LiveViewerRepository viewers;

	private final LiveTokens tokens;

	private final ConnectionService connections;

	private final ThreadService threads;

	private final ProfileService profiles;

	private final RealtimeService realtime;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final Clock clock;

	public LiveService(LiveSessionRepository sessions, LiveViewerRepository viewers, LiveTokens tokens,
			ConnectionService connections, ThreadService threads, ProfileService profiles, RealtimeService realtime,
			BlockChecker blocks, UserGuard guard, Clock clock) {
		this.sessions = sessions;
		this.viewers = viewers;
		this.tokens = tokens;
		this.connections = connections;
		this.threads = threads;
		this.profiles = profiles;
		this.realtime = realtime;
		this.blocks = blocks;
		this.guard = guard;
		this.clock = clock;
	}

	@Transactional
	public HostView start(String hostId, String threadId, String title) {
		guard.requireContactAllowed(hostId);
		if (!sessions.findLiveOf(hostId).isEmpty()) {
			throw ApiException.conflict("ALREADY_LIVE", "You're already live");
		}
		String thread = threadId == null || threadId.isBlank() ? null : threadId.strip();
		if (thread != null && !threads.isOpenMember(hostId, thread)) {
			throw ApiException.notFound("Thread");
		}
		String name = title == null || title.isBlank() ? null : title.strip();
		if (name != null && name.length() > 80) {
			throw ApiException.badRequest("TITLE_TOO_LONG", "Titles are up to 80 characters");
		}
		LiveSession session = sessions.save(new LiveSession(hostId, thread, name, clock.instant()));
		String hostName = firstName(hostId);
		LiveView announcement = view(session, hostName);
		audience(session).forEach(viewer -> realtime.toUser(viewer, "live", announcement));
		return new HostView(announcement, tokens.issue(session.getId(), identity(session, hostId), hostName, true), 0);
	}

	/** Lives the viewer may watch right now. */
	@Transactional(readOnly = true)
	public List<LiveView> available(String viewerId) {
		guard.requireActive(viewerId);
		Set<String> friends = connections.connectedUserIds(viewerId);
		Map<String, LiveSession> found = new LinkedHashMap<>();
		if (!friends.isEmpty()) {
			sessions.findLiveBy(friends).stream().filter(s -> s.getThreadId() == null).forEach(s -> found.put(s.getId(), s));
		}
		found.values().removeIf(s -> !mayWatch(viewerId, s));
		threadsOf(viewerId).forEach(s -> {
			if (mayWatch(viewerId, s)) {
				found.put(s.getId(), s);
			}
		});
		return found.values().stream().map(s -> view(s, firstName(s.getHostId()))).toList();
	}

	/** A viewer token, valid 10 minutes; the app calls this again to keep watching. */
	@Transactional
	public ViewerAccess join(String viewerId, String sessionId) {
		guard.requireActive(viewerId);
		LiveSession session = sessions.findById(sessionId).filter(LiveSession::isLive)
			.filter(s -> mayWatch(viewerId, s))
			.orElseThrow(() -> ApiException.notFound("Live"));
		if (session.getHostId().equals(viewerId)) {
			throw ApiException.unprocessable("HOST", "You're the host");
		}
		LiveViewer.Key key = new LiveViewer.Key(sessionId, viewerId);
		if (!viewers.existsById(key)) {
			viewers.save(new LiveViewer(sessionId, viewerId, clock.instant()));
		}
		String hostName = firstName(session.getHostId());
		return new ViewerAccess(view(session, hostName),
				tokens.issue(sessionId, identity(session, viewerId), firstName(viewerId), false));
	}

	@Transactional(readOnly = true)
	public HostView hostView(String hostId, String sessionId) {
		LiveSession session = sessions.findById(sessionId).filter(s -> s.getHostId().equals(hostId))
			.orElseThrow(() -> ApiException.notFound("Live"));
		return new HostView(view(session, firstName(hostId)), null, viewers.countFor(sessionId));
	}

	@Transactional
	public LiveView end(String hostId, String sessionId) {
		LiveSession session = sessions.findById(sessionId)
			.filter(s -> s.getHostId().equals(hostId) && s.isLive())
			.orElseThrow(() -> ApiException.notFound("Live"));
		return finish(session, "ENDED");
	}

	/** The host of a live, for blocking or reporting it from the viewer screen. */
	@Transactional(readOnly = true)
	public Optional<String> hostOf(String viewerId, String sessionId) {
		return sessions.findById(sessionId).filter(s -> mayWatch(viewerId, s)).map(LiveSession::getHostId);
	}

	@Scheduled(fixedDelayString = "${oneday.live.sweep-interval:PT1M}", initialDelayString = "PT1M")
	@Transactional
	public int sweep() {
		Instant now = clock.instant();
		List<LiveSession> tooLong = sessions.findLiveSince(now.minus(MAX_DURATION));
		tooLong.forEach(s -> finish(s, "TIME_LIMIT"));
		List<String> old = sessions.findIdsEndedBefore(now.minus(RETENTION));
		if (!old.isEmpty()) {
			viewers.deleteForSessions(old);
			sessions.deleteAllById(old);
		}
		return tooLong.size();
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> export(String userId) {
		return sessions.findByHostId(userId)
			.stream()
			.map(s -> Map.<String, Object>of("startedAt", s.getStartedAt(), "audience",
					s.getThreadId() == null ? "CONNECTIONS" : "THREAD"))
			.toList();
	}

	@Transactional
	public void forget(String userId) {
		List<String> hosted = sessions.findByHostId(userId).stream().map(LiveSession::getId).toList();
		if (!hosted.isEmpty()) {
			viewers.deleteForSessions(hosted);
			sessions.deleteAllById(hosted);
		}
		viewers.deleteByUser(userId);
	}

	private LiveView finish(LiveSession session, String reason) {
		session.end(reason, clock.instant());
		LiveView ended = view(session, firstName(session.getHostId()));
		audience(session).forEach(viewer -> realtime.toUser(viewer, "live", ended));
		realtime.toUser(session.getHostId(), "live", ended);
		return ended;
	}

	private boolean mayWatch(String viewerId, LiveSession session) {
		String host = session.getHostId();
		if (host.equals(viewerId)) {
			return true;
		}
		if (blocks.isBlockedEitherWay(viewerId, host)) {
			return false;
		}
		return session.getThreadId() == null ? connections.areConnected(viewerId, host)
				: threads.isOpenMember(viewerId, session.getThreadId());
	}

	private List<String> audience(LiveSession session) {
		String host = session.getHostId();
		Set<String> blocked = blocks.blockedEitherWay(host);
		List<String> people = session.getThreadId() == null ? new ArrayList<>(connections.connectedUserIds(host))
				: new ArrayList<>(threads.memberIds(session.getThreadId()));
		people.removeIf(p -> p.equals(host) || blocked.contains(p));
		return people;
	}

	private List<LiveSession> threadsOf(String viewerId) {
		return threads.mine(viewerId)
			.stream()
			.filter(t -> "IN".equals(t.myStatus()) && t.open())
			.flatMap(t -> sessions.findLiveInThreads(List.of(t.id())).stream())
			.toList();
	}

	/** Opaque and per-session: an SFU participant list never reveals who someone is across lives. */
	private static String identity(LiveSession session, String userId) {
		return Hashes.sha256("live|" + session.getId() + "|" + userId).substring(0, 16);
	}

	private LiveView view(LiveSession s, String hostName) {
		return new LiveView(s.getId(), hostName, s.getTitle(), s.getThreadId() == null ? "CONNECTIONS" : "THREAD",
				s.getThreadId(), s.isLive() ? "LIVE" : "ENDED", s.getStartedAt(), s.getStartedAt().plus(MAX_DURATION));
	}

	private String firstName(String userId) {
		return profiles.find(userId).map(Profile::firstName).orElse("Someone");
	}

	public record LiveView(String id, String hostFirstName, String title, String audience, String threadId,
			String state, Instant startedAt, Instant endsBy) {
	}

	/** {@code viewers}: people who joined, for the host only. {@code access} only on start. */
	public record HostView(LiveView live, Access access, long viewers) {
	}

	public record ViewerAccess(LiveView live, Access access) {
	}
}
