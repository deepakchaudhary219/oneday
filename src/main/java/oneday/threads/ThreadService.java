package oneday.threads;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.empathy.EmpathyMirror;
import oneday.empathy.EmpathyMirror.Concern;
import oneday.empathy.Tone;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
import oneday.identity.UserGuard;
import oneday.media.MediaKind;
import oneday.media.MediaService;
import oneday.notify.Notice;
import oneday.notify.NotificationService;
import oneday.platform.Hashes;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.realtime.RealtimeService;
import oneday.safety.BlockChecker;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collaborative Threads (v3; docs/07-v3-features.md): one shared story thread for a trip, a wedding or a
 * festival week, made by a person with up to 11 of their Connections, open for 1 to 7 days.
 *
 * <ul>
 * <li>Members only. Invitations go to the inviter's own Connections and need accepting.</li>
 * <li>Members needn't know each other, so everyone is shown by first name and a per-thread handle, never an
 * id. Blocks hide posts between the two people.</li>
 * <li>Captions get the Empathy Mirror. Posts are reportable by {@code threadPostId}.</li>
 * <li>A thread is read-only after it ends and deleted, with its media, three days later. Members keep what they
 * want on their phones; nothing becomes a permanent archive.</li>
 * </ul>
 */
@Service
public class ThreadService {

	static final int MAX_MEMBERS = 12;

	static final Duration RETENTION_AFTER_END = Duration.ofDays(3);

	private static final Set<ThreadMember.Status> CURRENT = EnumSet.of(ThreadMember.Status.INVITED,
			ThreadMember.Status.IN);

	private final ThreadRepository threads;

	private final ThreadMemberRepository members;

	private final ThreadPostRepository posts;

	private final ConnectionService connections;

	private final ProfileService profiles;

	private final MediaService media;

	private final EmpathyMirror empathy;

	private final NotificationService notifications;

	private final RealtimeService realtime;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final RateLimiter rateLimiter;

	private final EventPublisher events;

	private final Clock clock;

	public ThreadService(ThreadRepository threads, ThreadMemberRepository members, ThreadPostRepository posts,
			ConnectionService connections, ProfileService profiles, MediaService media, EmpathyMirror empathy,
			NotificationService notifications, RealtimeService realtime, BlockChecker blocks, UserGuard guard,
			RateLimiter rateLimiter, EventPublisher events, Clock clock) {
		this.threads = threads;
		this.members = members;
		this.posts = posts;
		this.connections = connections;
		this.profiles = profiles;
		this.media = media;
		this.empathy = empathy;
		this.notifications = notifications;
		this.realtime = realtime;
		this.blocks = blocks;
		this.guard = guard;
		this.rateLimiter = rateLimiter;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public ThreadView create(String creatorId, String title, int days, List<String> inviteConnectionIds) {
		guard.requireContactAllowed(creatorId);
		String name = title == null ? "" : title.strip();
		if (name.isEmpty() || name.length() > 60) {
			throw ApiException.badRequest("TITLE_REQUIRED", "Give the thread a title (up to 60 characters)");
		}
		if (days < 1 || days > 7) {
			throw ApiException.badRequest("INVALID_DURATION", "A thread lasts 1 to 7 days");
		}
		if (!rateLimiter.tryAcquire("threads:" + creatorId, 5, Duration.ofDays(1))) {
			throw ApiException.tooManyRequests("THREAD_RATE_LIMIT", "You've started enough threads for today");
		}
		Instant now = clock.instant();
		StoryThread thread = threads.save(new StoryThread(creatorId, name, now, now.plus(Duration.ofDays(days))));
		members.save(new ThreadMember(thread.getId(), creatorId, ThreadMember.Status.IN, creatorId, now));
		invite(thread, creatorId, inviteConnectionIds == null ? List.of() : inviteConnectionIds);
		return view(thread, creatorId);
	}

	/** Any member can invite their own Connections, up to 12 people in all. */
	@Transactional
	public ThreadView invite(String userId, String threadId, List<String> connectionIds) {
		StoryThread thread = requireOpenMember(userId, threadId);
		invite(thread, userId, connectionIds == null ? List.of() : connectionIds);
		return view(thread, userId);
	}

	@Transactional(readOnly = true)
	public List<ThreadView> mine(String userId) {
		guard.requireExisting(userId);
		return members.findByUser(userId, CURRENT)
			.stream()
			.map(m -> threads.findById(m.threadId()))
			.flatMap(Optional::stream)
			.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
			.map(t -> view(t, userId))
			.toList();
	}

	@Transactional(readOnly = true)
	public ThreadView get(String userId, String threadId) {
		StoryThread thread = requireThread(threadId);
		ThreadMember me = membership(threadId, userId).filter(m -> CURRENT.contains(m.getStatus()))
			.orElseThrow(() -> ApiException.notFound("Thread"));
		return view(thread, me.userId());
	}

	@Transactional
	public ThreadView respond(String userId, String threadId, boolean accept) {
		StoryThread thread = requireThread(threadId);
		ThreadMember me = membership(threadId, userId).filter(m -> m.getStatus() == ThreadMember.Status.INVITED)
			.orElseThrow(() -> ApiException.notFound("Invitation"));
		if (accept && !thread.isOpen(clock.instant())) {
			throw ApiException.conflict("THREAD_ENDED", "This thread has ended");
		}
		me.setStatus(accept ? ThreadMember.Status.IN : ThreadMember.Status.DECLINED, clock.instant());
		return view(thread, userId);
	}

	@Transactional
	public void leave(String userId, String threadId) {
		ThreadMember me = membership(threadId, userId).filter(ThreadMember::isIn)
			.orElseThrow(() -> ApiException.notFound("Thread"));
		me.setStatus(ThreadMember.Status.LEFT, clock.instant());
	}

	/** The creator removes someone by their per-thread handle. Silent for the removed person. */
	@Transactional
	public void remove(String userId, String threadId, String handle) {
		StoryThread thread = requireOpenMember(userId, threadId);
		if (!thread.getCreatorId().equals(userId)) {
			throw ApiException.forbidden("CREATOR_ONLY", "Only the person who started the thread can remove people");
		}
		ThreadMember target = members.findByThread(threadId)
			.stream()
			.filter(m -> handle(threadId, m.userId()).equals(handle) && !m.userId().equals(userId))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("Member"));
		target.setStatus(ThreadMember.Status.REMOVED, clock.instant());
	}

	@Transactional
	public PostView post(String userId, String threadId, ThreadPost.Kind kind, String caption, String mediaRef,
			boolean sendAnyway) {
		guard.requireContactAllowed(userId);
		StoryThread thread = requireOpenMember(userId, threadId);
		if (kind == null) {
			throw ApiException.badRequest("KIND_REQUIRED", "kind is TEXT, PHOTO or VIDEO");
		}
		String text = caption == null || caption.isBlank() ? null : caption.strip();
		if (text != null && text.length() > 200) {
			throw ApiException.badRequest("CAPTION_TOO_LONG", "Captions are up to 200 characters");
		}
		String ref = null;
		if (kind == ThreadPost.Kind.TEXT) {
			if (text == null) {
				throw ApiException.badRequest("CAPTION_REQUIRED", "A text post needs some text");
			}
		}
		else {
			if (mediaRef == null || mediaRef.isBlank()) {
				throw ApiException.badRequest("MEDIA_REQUIRED", "Photo and video posts need uploaded media");
			}
			ref = mediaRef.strip();
			media.requireAttachable(userId, ref, MediaKind.valueOf(kind.name()));
		}
		if (!rateLimiter.tryAcquire("thread-post:" + threadId + ":" + userId, 30, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("POST_RATE_LIMIT", "That's a lot of posts. Take a breather.");
		}
		var tone = text == null ? Optional.<Tone>empty() : empathy.reflect("thread", text, sendAnyway);
		ThreadPost post = posts.save(new ThreadPost(threadId, userId, kind, text, ref,
				tone.map(Enum::name).orElse(null), clock.instant()));
		if (ref != null) {
			events.publish(new DomainEvent.ThreadPostAdded(post.getId(), threadId, userId));
		}
		Set<String> blocked = blocks.blockedEitherWay(userId);
		members.findByThread(threadId)
			.stream()
			.filter(m -> m.isIn() && !m.userId().equals(userId) && !blocked.contains(m.userId()))
			.forEach(m -> realtime.toUser(m.userId(), "thread", new Ping(threadId, post.getId())));
		return postView(post, userId, firstName(userId));
	}

	/** Newest first; posts by people the viewer blocked (either way) are hidden. */
	@Transactional(readOnly = true)
	public List<PostView> posts(String userId, String threadId, Instant before, int limit) {
		requireThread(threadId);
		membership(threadId, userId).filter(ThreadMember::isIn).orElseThrow(() -> ApiException.notFound("Thread"));
		Set<String> blocked = blocks.blockedEitherWay(userId);
		Map<String, String> names = new HashMap<>();
		return posts
			.findPage(threadId, before == null ? clock.instant().plusSeconds(1) : before,
					PageRequest.of(0, Math.clamp(limit, 1, 50)))
			.stream()
			.filter(p -> !blocked.contains(p.getAuthorId()))
			.map(p -> postView(p, userId, names.computeIfAbsent(p.getAuthorId(), this::firstName)))
			.toList();
	}

	@Transactional
	public void deletePost(String userId, String threadId, String postId) {
		ThreadPost post = posts.findById(postId)
			.filter(p -> p.getThreadId().equals(threadId) && p.getAuthorId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("Post"));
		discardMedia(post);
		posts.delete(post);
	}

	/** Whether the person is currently in the thread and it is still open (Circle Live audiences). */
	@Transactional(readOnly = true)
	public boolean isOpenMember(String userId, String threadId) {
		return threads.findById(threadId).filter(t -> t.isOpen(clock.instant())).isPresent()
				&& membership(threadId, userId).filter(ThreadMember::isIn).isPresent();
	}

	@Transactional(readOnly = true)
	public List<String> memberIds(String threadId) {
		return members.findByThread(threadId).stream().filter(ThreadMember::isIn).map(ThreadMember::userId).toList();
	}

	/** The author of a post, for blocking or reporting it; only current members can name one. */
	@Transactional(readOnly = true)
	public Optional<String> authorOf(String viewerId, String postId) {
		return posts.findById(postId)
			.filter(p -> membership(p.getThreadId(), viewerId).filter(ThreadMember::isIn).isPresent())
			.map(ThreadPost::getAuthorId);
	}

	/** Outbox consumer: durable media copy, since a thread may outlive the story bucket's expiry. */
	@Transactional
	public void preserveMedia(String postId) {
		posts.findById(postId)
			.filter(p -> p.getMediaRef() != null && p.getDurableMediaRef() == null)
			.ifPresent(p -> p.preserved(media.copyToDurable("threads", p.getMediaRef(),
					p.getKind() == ThreadPost.Kind.VIDEO)));
	}

	@Scheduled(fixedDelayString = "${oneday.threads.sweep-interval:PT1H}", initialDelayString = "PT5M")
	@Transactional
	public int purgeEnded() {
		List<String> ended = threads.findByEndsAtBefore(clock.instant().minus(RETENTION_AFTER_END))
			.stream()
			.map(StoryThread::getId)
			.toList();
		deleteThreads(ended);
		return ended.size();
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> export(String userId) {
		List<Map<String, Object>> out = new ArrayList<>();
		posts.findByAuthorId(userId)
			.forEach(p -> out.add(Map.of("threadId", p.getThreadId(), "kind", p.getKind().name(), "caption",
					p.getCaption() == null ? "" : p.getCaption(), "postedAt", p.getCreatedAt())));
		return out;
	}

	/** Erasure: the person's posts and memberships go, and threads they started go with everyone's posts. */
	@Transactional
	public void forget(String userId) {
		deleteThreads(threads.findByCreatorId(userId).stream().map(StoryThread::getId).toList());
		posts.findByAuthorId(userId).forEach(this::discardMedia);
		posts.deleteByAuthor(userId);
		members.deleteByUser(userId);
	}

	private void deleteThreads(List<String> threadIds) {
		if (threadIds.isEmpty()) {
			return;
		}
		posts.findByThreadIdIn(threadIds).forEach(this::discardMedia);
		posts.deleteByThreads(threadIds);
		members.deleteByThreads(threadIds);
		threads.deleteAllById(threadIds);
	}

	private void discardMedia(ThreadPost post) {
		media.discardObject(post.getDurableMediaRef());
	}

	private void invite(StoryThread thread, String inviterId, List<String> connectionIds) {
		Instant now = clock.instant();
		Map<String, ThreadMember> existing = new HashMap<>();
		members.findByThread(thread.getId()).forEach(m -> existing.put(m.userId(), m));
		long current = existing.values().stream().filter(m -> CURRENT.contains(m.getStatus())).count();
		if (current + connectionIds.size() > MAX_MEMBERS) {
			throw ApiException.unprocessable("THREAD_FULL", "A thread has at most 12 people");
		}
		String inviterName = firstName(inviterId);
		for (String connectionId : connectionIds) {
			Connection connection = connections.requireMember(connectionId, inviterId);
			String invitee = connection.otherThan(inviterId);
			if (!connection.isActive() || blocks.isBlockedEitherWay(inviterId, invitee)
					|| guard.reachableAmong(List.of(invitee)).isEmpty()) {
				throw ApiException.conflict("CONNECTION_INACTIVE", "One of those connections is no longer active");
			}
			ThreadMember already = existing.get(invitee);
			if (already != null && already.getStatus() != ThreadMember.Status.DECLINED
					&& already.getStatus() != ThreadMember.Status.LEFT) {
				continue; // invited, in, or removed (a removal can't be undone by another member)
			}
			if (already != null) {
				already.setStatus(ThreadMember.Status.INVITED, now);
			}
			else {
				members.save(new ThreadMember(thread.getId(), invitee, ThreadMember.Status.INVITED, inviterId, now));
			}
			notifications.notice(invitee, Notice.Kind.SOCIAL,
					inviterName + " invited you to the thread \"" + thread.getTitle() + "\"");
		}
	}

	private StoryThread requireOpenMember(String userId, String threadId) {
		StoryThread thread = requireThread(threadId);
		membership(threadId, userId).filter(ThreadMember::isIn).orElseThrow(() -> ApiException.notFound("Thread"));
		if (!thread.isOpen(clock.instant())) {
			throw ApiException.conflict("THREAD_ENDED", "This thread has ended and is read-only");
		}
		return thread;
	}

	private StoryThread requireThread(String threadId) {
		return threads.findById(threadId).orElseThrow(() -> ApiException.notFound("Thread"));
	}

	private Optional<ThreadMember> membership(String threadId, String userId) {
		return members.findById(new ThreadMember.Key(threadId, userId));
	}

	private ThreadView view(StoryThread thread, String viewerId) {
		Set<String> blocked = blocks.blockedEitherWay(viewerId);
		ThreadMember me = membership(thread.getId(), viewerId).orElseThrow();
		List<MemberView> people = members.findByThread(thread.getId())
			.stream()
			.filter(m -> m.isIn() && !blocked.contains(m.userId()))
			.map(m -> new MemberView(handle(thread.getId(), m.userId()), firstName(m.userId()),
					m.userId().equals(thread.getCreatorId()), m.userId().equals(viewerId)))
			.toList();
		return new ThreadView(thread.getId(), thread.getTitle(), me.getStatus().name(),
				thread.isOpen(clock.instant()), thread.getCreatorId().equals(viewerId), thread.getEndsAt(), people);
	}

	private PostView postView(ThreadPost p, String viewerId, String firstName) {
		boolean mine = p.getAuthorId().equals(viewerId);
		return new PostView(p.getId(), handle(p.getThreadId(), p.getAuthorId()), firstName, mine, p.getKind().name(),
				p.getCaption(), p.servedMediaRef() == null ? null : media.viewUrl(p.servedMediaRef()), p.getCreatedAt(),
				EmpathyMirror.concernFor(p.getToneFlag(), mine));
	}

	/** Stable within a thread, meaningless across threads: no cross-thread tracking of a person. */
	static String handle(String threadId, String userId) {
		return Hashes.sha256("thread|" + threadId + "|" + userId).substring(0, 12);
	}

	private String firstName(String userId) {
		return profiles.find(userId).map(Profile::firstName).orElse("Someone");
	}

	/** {@code myStatus}: INVITED or IN. */
	public record ThreadView(String id, String title, String myStatus, boolean open, boolean youStartedIt,
			Instant endsAt, List<MemberView> members) {
	}

	public record MemberView(String handle, String firstName, boolean startedIt, boolean you) {
	}

	public record PostView(String id, String authorHandle, String firstName, boolean mine, String kind,
			String caption, String mediaUrl, Instant postedAt, Concern concern) {
	}

	public record Ping(String threadId, String postId) {
	}
}
