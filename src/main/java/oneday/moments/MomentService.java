package oneday.moments;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import oneday.common.ApiException;
import oneday.common.ProductMetrics;
import oneday.config.OneDayProperties;
import oneday.connections.ConnectionService;
import oneday.events.DomainEvent;
import oneday.events.EventPublisher;
import oneday.geo.GeoCell;
import oneday.geo.GeoMath;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.media.MediaKind;
import oneday.media.MediaService;
import oneday.profile.ActivityTags;
import oneday.profile.ProfileService;
import oneday.prompts.PromptCatalog;
import oneday.safety.BlockChecker;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MomentService {

	private static final int MAX_CANDIDATES = 500;

	/** A relay is a conversation in stories, not a pile-on: it closes after this many links. */
	static final int MAX_RELAY_LENGTH = 30;

	private final MomentRepository moments;

	private final LocationService locations;

	private final ProfileService profiles;

	private final ConnectionService connections;

	private final UserGuard guard;

	private final BlockChecker blocks;

	private final MediaService media;

	private final Clock clock;

	private final ProductMetrics metrics;

	private final EventPublisher events;

	private final OneDayProperties.Moments settings;

	private final PromptCatalog prompts;

	public MomentService(MomentRepository moments, LocationService locations, ProfileService profiles,
			ConnectionService connections, UserGuard guard, BlockChecker blocks, MediaService media, Clock clock,
			OneDayProperties properties, ProductMetrics metrics, EventPublisher events, PromptCatalog prompts) {
		this.prompts = prompts;
		this.metrics = metrics;
		this.events = events;
		this.moments = moments;
		this.locations = locations;
		this.profiles = profiles;
		this.connections = connections;
		this.guard = guard;
		this.blocks = blocks;
		this.media = media;
		this.clock = clock;
		this.settings = properties.moments();
	}

	/**
	 * Publishing reaches other people, so it needs verification. Public shares must be live captures
	 * (Honest capture, blueprint v2 §8) and are stamped with the poster's current cell.
	 */
	@Transactional
	public MomentView publish(String userId, CreateMomentRequest request) {
		guard.requireContactAllowed(userId);
		boolean isText = request.kind() == MomentKind.TEXT;
		if (isText && (request.caption() == null || request.caption().isBlank())) {
			throw ApiException.badRequest("CAPTION_REQUIRED", "A text moment needs some text");
		}
		if (!isText) {
			if (request.mediaRef() == null || request.mediaRef().isBlank()) {
				throw ApiException.badRequest("MEDIA_REQUIRED", "Photo and video moments need uploaded media");
			}
			// Only the poster's own recent upload, used once: nobody can attach someone else's photo.
			media.requireAttachable(userId, request.mediaRef().trim(), MediaKind.valueOf(request.kind().name()));
			if (moments.existsByMediaRef(request.mediaRef().trim())) {
				throw ApiException.conflict("MEDIA_ALREADY_USED", "That upload is already attached to a moment");
			}
		}
		String promptKey = blankToNull(request.promptKey());
		if (promptKey != null && !prompts.isTodays(userId, promptKey)) {
			throw ApiException.unprocessable("PROMPT_NOT_TODAY", "That prompt has closed. Answer today's instead.");
		}
		Moment replyTo = null;
		if (request.replyToMomentId() != null && !request.replyToMomentId().isBlank()) {
			if (request.shareScope() != ShareScope.PUBLIC_DISCOVERY) {
				throw ApiException.unprocessable("RELAY_MUST_BE_PUBLIC", "A relay answer is a public moment");
			}
			replyTo = requireRelayable(userId, request.replyToMomentId().trim());
		}
		GeoCell cell = null;
		if (request.shareScope() == ShareScope.PUBLIC_DISCOVERY) {
			if (!isText && !request.isCapturedLive()) {
				throw ApiException.unprocessable("LIVE_CAPTURE_REQUIRED",
						"Public moments must be captured live in the app");
			}
			cell = locations.requireCurrentCell(userId);
		}
		Instant now = clock.instant();
		Moment draft = new Moment(userId, request.kind(), blankToNull(request.caption()),
				ActivityTags.normalize(request.activityTag()), blankToNull(request.mediaRef()),
				request.isPreviewAllowed(), request.shareScope(), isText || request.isCapturedLive(), cell, now,
				now.plus(settings.ttl()));
		if (promptKey != null) {
			draft.answerPrompt(promptKey);
		}
		if (replyTo != null) {
			draft.joinRelay(replyTo);
		}
		Moment moment = moments.save(draft);
		metrics.momentPublished(moment.getKind());
		events.publish(new DomainEvent.MomentPublished(moment.getId(), userId, moment.getKind().name(),
				moment.getShareScope().name()));
		return full(moment, profiles.require(userId).firstName());
	}

	/**
	 * Owner and Connections see the full moment. Strangers see only the Layer-0 ambient view of public
	 * shares, and only while the owner is still sharing location. Anything else is "not found", so the
	 * existence of private content is never revealed.
	 */
	@Transactional(readOnly = true)
	public MomentView view(String viewerId, String momentId) {
		Moment moment = moments.findById(momentId)
			.filter(m -> m.isLive(clock.instant()))
			.orElseThrow(() -> ApiException.notFound("Moment"));
		String ownerId = moment.getOwnerId();
		String firstName = profiles.require(ownerId).firstName();
		if (ownerId.equals(viewerId)) {
			return full(moment, firstName);
		}
		if (blocks.isBlockedEitherWay(viewerId, ownerId)) {
			throw ApiException.notFound("Moment");
		}
		if (connections.areConnected(viewerId, ownerId)) {
			return full(moment, firstName);
		}
		if (moment.isPublic() && locations.currentCell(ownerId).isPresent()) {
			return MomentView.ambient(moment, firstName, previewUrl(moment));
		}
		throw ApiException.notFound("Moment");
	}

	/**
	 * Story Relay: the public moments that answered one another, in order. Each viewer sees the links they
	 * may see (the same rule as a single moment: Layer 0 for strangers who still share location, nothing for
	 * blocked pairs), so a relay never leaks who is in it.
	 */
	@Transactional(readOnly = true)
	public RelayView relay(String viewerId, String momentId) {
		view(viewerId, momentId); // the same visibility rule as opening the moment itself
		Moment moment = moments.findById(momentId).orElseThrow(() -> ApiException.notFound("Moment"));
		String rootId = moment.getRelayRootId() != null ? moment.getRelayRootId() : moment.getId();
		Instant now = clock.instant();
		List<Moment> chain = new ArrayList<>();
		moments.findById(rootId).filter(m -> m.isLive(now)).ifPresent(chain::add);
		chain.addAll(moments.findByRelayRootIdAndExpiresAtAfterOrderByRelayDepthAscCreatedAtAsc(rootId, now,
				PageRequest.of(0, MAX_RELAY_LENGTH)));
		Set<String> blocked = blocks.blockedEitherWay(viewerId);
		Set<String> owners = chain.stream().map(Moment::getOwnerId).collect(Collectors.toSet());
		Set<String> reachable = guard.reachableAmong(owners);
		Set<String> sharing = locations.currentCells(owners).keySet();
		Set<String> connected = connections.connectedUserIds(viewerId);
		List<RelayLink> links = chain.stream().filter(m -> {
			String owner = m.getOwnerId();
			return owner.equals(viewerId) || connected.contains(owner)
					|| (!blocked.contains(owner) && reachable.contains(owner) && sharing.contains(owner));
		}).map(m -> new RelayLink(m.getId(), profiles.require(m.getOwnerId()).firstName(), m.getKind(),
				m.getActivityTag(), previewUrl(m), m.isCapturedLive(), m.getRelayDepth(),
				m.getOwnerId().equals(viewerId))).toList();
		String activity = chain.isEmpty() ? null : chain.get(0).getActivityTag();
		boolean open = chain.size() < MAX_RELAY_LENGTH;
		return new RelayView(rootId, activity, links.size(), open, links);
	}

	/** How many live answers each relay root has (for the Story Map and the Local Pulse). */
	@Transactional(readOnly = true)
	public Map<String, Long> relayCounts(Collection<String> rootIds) {
		if (rootIds.isEmpty()) {
			return Map.of();
		}
		Map<String, Long> counts = new HashMap<>();
		for (Object[] row : moments.countRelays(rootIds, clock.instant())) {
			counts.put((String) row[0], (Long) row[1]);
		}
		return counts;
	}

	/** The person's own live answers to a prompt. */
	@Transactional(readOnly = true)
	public List<Moment> answersBy(String userId, String promptKey) {
		return moments.findByOwnerIdAndPromptKeyAndExpiresAtAfter(userId, promptKey, clock.instant());
	}

	@Transactional(readOnly = true)
	public List<Moment> liveBy(String userId) {
		return moments.findByOwnerIdAndExpiresAtAfterOrderByCreatedAtDesc(userId, clock.instant());
	}

	@Transactional(readOnly = true)
	public List<MomentView> mine(String userId) {
		String firstName = profiles.require(userId).firstName();
		return moments.findByOwnerIdAndExpiresAtAfterOrderByCreatedAtDesc(userId, clock.instant())
			.stream()
			.map(m -> full(m, firstName))
			.toList();
	}

	@Transactional
	public void delete(String userId, String momentId) {
		Moment moment = moments.findById(momentId)
			.filter(m -> m.getOwnerId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("Moment"));
		moments.delete(moment);
		media.discard(moment.getMediaRef());
	}

	/** A public moment that is still live, for signalling. */
	@Transactional(readOnly = true)
	public Moment requireLivePublic(String momentId) {
		return moments.findById(momentId)
			.filter(m -> m.isPublic() && m.isLive(clock.instant()))
			.orElseThrow(() -> ApiException.notFound("Moment"));
	}

	@Transactional(readOnly = true)
	public Optional<Moment> find(String momentId) {
		return moments.findById(momentId);
	}

	/** Live public moments whose capture cell falls inside the bounding box of the given radius. */
	@Transactional(readOnly = true)
	public List<Moment> livePublicNear(GeoCell center, double radiusKm) {
		double[] half = GeoMath.boundingBoxDegrees(center.lat(), radiusKm);
		return moments.findPublicInBox(clock.instant(), center.lat() - half[0], center.lat() + half[0],
				center.lon() - half[1], center.lon() + half[1], PageRequest.of(0, MAX_CANDIDATES));
	}

	@Transactional(readOnly = true)
	public List<Moment> allBy(String userId) {
		return moments.findByOwnerIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public void deleteAllBy(String userId) {
		moments.deleteByOwner(userId);
	}

	/** Short-lived URL of the Layer-0 preview, if this moment has one. */
	public String previewUrl(Moment moment) {
		return moment.hasPreview() ? media.previewUrl(moment.getMediaRef()) : null;
	}

	private Moment requireRelayable(String userId, String replyToId) {
		Moment target = requireLivePublic(replyToId);
		String owner = target.getOwnerId();
		if (owner.equals(userId)) {
			throw ApiException.unprocessable("CANNOT_RELAY_SELF", "Relays are answered by other people");
		}
		if (blocks.isBlockedEitherWay(userId, owner) || guard.reachableAmong(List.of(owner)).isEmpty()
				|| locations.currentCell(owner).isEmpty() || !connections.inCouple(List.of(userId, owner)).isEmpty()) {
			throw ApiException.notFound("Moment");
		}
		String rootId = target.getRelayRootId() != null ? target.getRelayRootId() : target.getId();
		boolean ownsRoot = moments.findById(rootId).map(r -> r.getOwnerId().equals(userId)).orElse(false);
		if (ownsRoot || moments.existsByRelayRootIdAndOwnerId(rootId, userId)) {
			throw ApiException.conflict("ALREADY_IN_RELAY", "You're already part of this relay");
		}
		if (target.getRelayDepth() + 1 >= MAX_RELAY_LENGTH) {
			throw ApiException.conflict("RELAY_FULL", "This relay is complete. Start your own!");
		}
		return target;
	}

	private MomentView full(Moment moment, String firstName) {
		return MomentView.full(moment, firstName, previewUrl(moment), media.viewUrl(moment.getMediaRef()));
	}

	/** A link in a relay, Layer 0 only. {@code position} 0 is the moment that started it. */
	public record RelayLink(String momentId, String firstName, MomentKind kind, String activity, String previewUrl,
			boolean liveCaptured, int position, boolean mine) {
	}

	public record RelayView(String relayId, String activity, int length, boolean open, List<RelayLink> links) {
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
