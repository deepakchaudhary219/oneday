package oneday.moments;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import oneday.common.ApiException;
import oneday.identity.UserGuard;
import oneday.notify.Notice;
import oneday.notify.NotificationService;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spotlight Replies (v3; docs/07-v3-features.md): the owner of a public story can highlight up to three Story
 * Relay answers to it. A spotlight shows only after the answer's author accepts, and either side can take it
 * back at any time. Spotlit answers lead the relay; nothing else changes about who can see what.
 */
@Service
public class SpotlightService {

	static final int MAX_PER_STORY = 3;

	private final SpotlightRepository spotlights;

	private final MomentRepository moments;

	private final ProfileService profiles;

	private final NotificationService notifications;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final Clock clock;

	public SpotlightService(SpotlightRepository spotlights, MomentRepository moments, ProfileService profiles,
			NotificationService notifications, BlockChecker blocks, UserGuard guard, Clock clock) {
		this.spotlights = spotlights;
		this.moments = moments;
		this.profiles = profiles;
		this.notifications = notifications;
		this.blocks = blocks;
		this.guard = guard;
		this.clock = clock;
	}

	@Transactional
	public SpotlightView propose(String ownerId, String rootMomentId, String replyMomentId) {
		guard.requireContactAllowed(ownerId);
		Moment root = moments.findById(rootMomentId)
			.filter(m -> m.getOwnerId().equals(ownerId) && m.isLive(clock.instant()) && m.isPublic())
			.orElseThrow(() -> ApiException.notFound("Moment"));
		Moment reply = moments.findById(replyMomentId)
			.filter(m -> root.getId().equals(m.getRelayRootId()) && m.isLive(clock.instant()))
			.orElseThrow(() -> ApiException.notFound("Relay answer"));
		if (reply.getOwnerId().equals(ownerId)) {
			throw ApiException.unprocessable("OWN_ANSWER", "Spotlight someone else's answer");
		}
		if (blocks.isBlockedEitherWay(ownerId, reply.getOwnerId())) {
			throw ApiException.notFound("Relay answer");
		}
		if (spotlights.findByReplyMomentId(replyMomentId).isPresent()) {
			throw ApiException.conflict("ALREADY_ASKED", "You've already asked about this answer");
		}
		if (spotlights.countByRootMomentIdAndStatusNot(rootMomentId, Spotlight.Status.DECLINED) >= MAX_PER_STORY) {
			throw ApiException.conflict("SPOTLIGHT_FULL", "A story can spotlight three answers");
		}
		Spotlight spotlight = spotlights.save(new Spotlight(rootMomentId, replyMomentId, ownerId,
				reply.getOwnerId(), clock.instant()));
		notifications.notice(reply.getOwnerId(), Notice.Kind.SOCIAL,
				firstName(ownerId) + " would like to spotlight your answer to their story. It's your call.");
		return view(spotlight, ownerId);
	}

	@Transactional(readOnly = true)
	public List<SpotlightView> requests(String userId) {
		guard.requireExisting(userId);
		return spotlights.findByReplierIdAndStatus(userId, Spotlight.Status.PENDING)
			.stream()
			.filter(s -> !blocks.isBlockedEitherWay(userId, s.getOwnerId()))
			.map(s -> view(s, userId))
			.toList();
	}

	@Transactional
	public SpotlightView respond(String userId, String spotlightId, boolean accept) {
		Spotlight spotlight = spotlights.findById(spotlightId)
			.filter(s -> s.getReplierId().equals(userId) && s.getStatus() == Spotlight.Status.PENDING)
			.orElseThrow(() -> ApiException.notFound("Spotlight request"));
		spotlight.setStatus(accept ? Spotlight.Status.ACCEPTED : Spotlight.Status.DECLINED);
		return view(spotlight, userId);
	}

	/** Either side can take a spotlight back at any time. */
	@Transactional
	public void withdraw(String userId, String spotlightId) {
		Spotlight spotlight = spotlights.findById(spotlightId)
			.filter(s -> s.getOwnerId().equals(userId) || s.getReplierId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("Spotlight"));
		spotlights.delete(spotlight);
	}

	/** Accepted spotlights on a story, for the relay view. */
	@Transactional(readOnly = true)
	public Set<String> spotlitReplies(String rootMomentId) {
		return spotlights.findByRootMomentIdAndStatus(rootMomentId, Spotlight.Status.ACCEPTED)
			.stream()
			.map(Spotlight::getReplyMomentId)
			.collect(Collectors.toSet());
	}

	@Scheduled(fixedDelayString = "${oneday.spotlights.sweep-interval:PT1H}", initialDelayString = "PT7M")
	@Transactional
	public int purgeOrphans() {
		return spotlights.deleteOrphans();
	}

	@Transactional
	public void forget(String userId) {
		spotlights.deleteInvolving(userId);
	}

	private SpotlightView view(Spotlight s, String viewerId) {
		boolean asOwner = s.getOwnerId().equals(viewerId);
		return new SpotlightView(s.getId(), s.getRootMomentId(), s.getReplyMomentId(),
				firstName(asOwner ? s.getReplierId() : s.getOwnerId()), asOwner ? "OWNER" : "ANSWERER",
				s.getStatus().name());
	}

	private String firstName(String userId) {
		return profiles.find(userId).map(Profile::firstName).orElse("Someone");
	}

	/** {@code withFirstName}: the other person; {@code role}: the viewer's side of it. */
	public record SpotlightView(String id, String storyMomentId, String answerMomentId, String withFirstName,
			String role, String status) {
	}
}
