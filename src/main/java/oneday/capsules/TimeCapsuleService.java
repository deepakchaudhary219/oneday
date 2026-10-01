package oneday.capsules;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import oneday.common.ApiException;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
import oneday.identity.UserGuard;
import oneday.media.MediaKind;
import oneday.media.MediaService;
import oneday.notify.Notice;
import oneday.notify.NotificationService;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Time Capsules (blueprint v2 §11, v3; see docs/07-v3-features.md): a message, optionally with a photo or
 * video from the camera, sealed for yourself or a Connection until a day you choose (1 day to 5 years ahead).
 *
 * <ul>
 * <li>Until it opens, the recipient sees only who it's from and when it opens; a capsule to yourself hides its
 * content from you too, which is the point.</li>
 * <li>On the day it opens the recipient gets one notice and one push: an event they're waiting for, not
 * bait.</li>
 * <li>A block cancels capsules between the two; a capsule to someone who is no longer a Connection when it is
 * due is cancelled rather than delivered.</li>
 * <li>Media is copied out of the expiring story prefix off-request, so it survives until the day.</li>
 * </ul>
 */
@Service
public class TimeCapsuleService {

	static final Duration MIN_DELAY = Duration.ofDays(1);

	static final Duration MAX_DELAY = Duration.ofDays(5 * 365);

	static final int MAX_SEALED = 20;

	private final TimeCapsuleRepository capsules;

	private final ConnectionService connections;

	private final ProfileService profiles;

	private final MediaService media;

	private final NotificationService notifications;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final EventPublisher events;

	private final Clock clock;

	public TimeCapsuleService(TimeCapsuleRepository capsules, ConnectionService connections, ProfileService profiles,
			MediaService media, NotificationService notifications, BlockChecker blocks, UserGuard guard,
			EventPublisher events, Clock clock) {
		this.capsules = capsules;
		this.connections = connections;
		this.profiles = profiles;
		this.media = media;
		this.notifications = notifications;
		this.blocks = blocks;
		this.guard = guard;
		this.events = events;
		this.clock = clock;
	}

	/** @param connectionId null for a capsule to yourself */
	@Transactional
	public CapsuleView seal(String senderId, String connectionId, String message, MediaKind mediaKind,
			String mediaRef, Instant opensAt) {
		guard.requireActive(senderId);
		String text = message == null ? "" : message.strip();
		if (text.isEmpty() || text.length() > 1000) {
			throw ApiException.badRequest("MESSAGE_REQUIRED", "Write something to seal (up to 1000 characters)");
		}
		Instant now = clock.instant();
		if (opensAt == null || opensAt.isBefore(now.plus(MIN_DELAY)) || opensAt.isAfter(now.plus(MAX_DELAY))) {
			throw ApiException.badRequest("INVALID_OPEN_DATE", "Choose a day between tomorrow and five years from now");
		}
		String recipientId = senderId;
		if (connectionId != null) {
			guard.requireContactAllowed(senderId);
			Connection connection = connections.requireMember(connectionId, senderId);
			recipientId = connection.otherThan(senderId);
			if (!connection.isActive() || blocks.isBlockedEitherWay(senderId, recipientId)) {
				throw ApiException.conflict("CONNECTION_INACTIVE", "This connection is no longer active");
			}
		}
		if (capsules.countBySenderIdAndStatus(senderId, TimeCapsule.Status.SEALED) >= MAX_SEALED) {
			throw ApiException.conflict("TOO_MANY_CAPSULES", "You have 20 capsules waiting. Let some open first.");
		}
		String ref = null;
		if (mediaRef != null && !mediaRef.isBlank()) {
			if (mediaKind == null) {
				throw ApiException.badRequest("MEDIA_KIND_REQUIRED", "Say whether the media is a PHOTO or a VIDEO");
			}
			ref = mediaRef.strip();
			media.requireAttachable(senderId, ref, mediaKind);
		}
		TimeCapsule capsule = capsules.save(new TimeCapsule(senderId, recipientId, connectionId, text,
				ref == null ? null : mediaKind, ref, opensAt, now));
		if (ref != null) {
			events.publish(new DomainEvent.CapsuleSealed(capsule.getId(), senderId));
		}
		return view(capsule, senderId);
	}

	@Transactional(readOnly = true)
	public List<CapsuleView> sent(String userId) {
		guard.requireExisting(userId);
		return capsules.findSentBy(userId).stream().map(c -> view(c, userId)).toList();
	}

	@Transactional(readOnly = true)
	public List<CapsuleView> incoming(String userId) {
		guard.requireExisting(userId);
		return capsules.findIncomingFor(userId)
			.stream()
			.filter(c -> !blocks.isBlockedEitherWay(userId, c.getSenderId()))
			.map(c -> view(c, userId))
			.toList();
	}

	@Transactional(readOnly = true)
	public CapsuleView get(String userId, String capsuleId) {
		return view(requireVisible(userId, capsuleId), userId);
	}

	/** The sender can cancel until it opens; the media goes with it. */
	@Transactional
	public void cancel(String userId, String capsuleId) {
		TimeCapsule capsule = capsules.findById(capsuleId)
			.filter(c -> c.getSenderId().equals(userId) && c.getStatus() != TimeCapsule.Status.CANCELLED)
			.orElseThrow(() -> ApiException.notFound("Capsule"));
		if (capsule.getStatus() == TimeCapsule.Status.OPENED) {
			throw ApiException.conflict("ALREADY_OPENED", "This capsule has already opened");
		}
		cancelAndDiscard(capsule);
	}

	/** Opens due capsules (every minute; reads also treat a due capsule as open). */
	@Scheduled(fixedDelayString = "${oneday.capsules.open-interval:PT1M}", initialDelayString = "PT1M")
	@Transactional
	public int openDue() {
		Instant now = clock.instant();
		List<TimeCapsule> due = capsules.findDue(now, PageRequest.of(0, 500));
		for (TimeCapsule capsule : due) {
			if (!capsule.isToSelf() && !connections.areConnected(capsule.getSenderId(), capsule.getRecipientId())) {
				cancelAndDiscard(capsule);
				continue;
			}
			capsule.open(now);
			String from = capsule.isToSelf() ? "you" : firstName(capsule.getSenderId());
			notifications.notice(capsule.getRecipientId(), Notice.Kind.SOCIAL,
					"A time capsule from " + from + " just opened.");
			notifications.requestPush(capsule.getRecipientId(), "A time capsule opened",
					"From " + from + ", sealed " + capsule.getCreatedAt().toString().substring(0, 10),
					Map.of("type", "capsule", "capsuleId", capsule.getId()));
		}
		return due.size();
	}

	/** Outbox consumer: the durable media copy (idempotent). */
	@Transactional
	public void preserveMedia(String capsuleId) {
		capsules.findById(capsuleId)
			.filter(c -> c.getStatus() != TimeCapsule.Status.CANCELLED && c.getMediaRef() != null
					&& c.getDurableMediaRef() == null)
			.ifPresent(c -> c.preserved(media.copyToDurable("capsules", c.getMediaRef(),
					c.getMediaKind() == MediaKind.VIDEO)));
	}

	@Transactional
	public void cancelBetween(String a, String b) {
		capsules.findSealedBetween(a, b).forEach(this::cancelAndDiscard);
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> export(String userId) {
		return capsules.findInvolving(userId)
			.stream()
			.filter(c -> c.getSenderId().equals(userId) || isOpen(c, clock.instant()))
			.map(c -> Map.<String, Object>of("direction", c.getSenderId().equals(userId) ? "SENT" : "RECEIVED",
					"message", c.getMessage(), "opensAt", c.getOpensAt(), "status", c.getStatus().name()))
			.toList();
	}

	@Transactional
	public void forget(String userId) {
		capsules.findInvolving(userId).forEach(c -> media.discardObject(c.getDurableMediaRef()));
		capsules.deleteInvolving(userId);
	}

	private void cancelAndDiscard(TimeCapsule capsule) {
		capsule.cancel();
		media.discardObject(capsule.getDurableMediaRef());
	}

	private TimeCapsule requireVisible(String userId, String capsuleId) {
		return capsules.findById(capsuleId)
			.filter(c -> c.getStatus() != TimeCapsule.Status.CANCELLED)
			.filter(c -> c.getSenderId().equals(userId) || c.getRecipientId().equals(userId))
			.filter(c -> c.getSenderId().equals(userId) || !blocks.isBlockedEitherWay(userId, c.getSenderId()))
			.orElseThrow(() -> ApiException.notFound("Capsule"));
	}

	private boolean isOpen(TimeCapsule capsule, Instant now) {
		return capsule.getStatus() == TimeCapsule.Status.OPENED
				|| (capsule.getStatus() == TimeCapsule.Status.SEALED && !capsule.getOpensAt().isAfter(now));
	}

	/**
	 * Content shows once open. Before that only the sender of a capsule to someone else sees it (they wrote
	 * it); a capsule to yourself stays sealed even from you.
	 */
	private CapsuleView view(TimeCapsule c, String viewerId) {
		boolean open = isOpen(c, clock.instant());
		boolean sender = c.getSenderId().equals(viewerId);
		boolean showContent = open || (sender && !c.isToSelf());
		String direction = c.isToSelf() ? "SELF" : sender ? "SENT" : "RECEIVED";
		String other = c.isToSelf() ? null : firstName(sender ? c.getRecipientId() : c.getSenderId());
		return new CapsuleView(c.getId(), direction, other, open ? "OPEN" : "SEALED", c.getOpensAt(),
				c.getCreatedAt(), showContent ? c.getMessage() : null, c.getMediaKind(),
				showContent && c.servedMediaRef() != null ? media.viewUrl(c.servedMediaRef()) : null);
	}

	private String firstName(String userId) {
		return profiles.find(userId).map(Profile::firstName).orElse("Someone");
	}

	/** {@code withFirstName} is the other person (null for a capsule to yourself). */
	public record CapsuleView(String id, String direction, String withFirstName, String state, Instant opensAt,
			Instant sealedAt, String message, MediaKind mediaKind, String mediaUrl) {
	}
}
