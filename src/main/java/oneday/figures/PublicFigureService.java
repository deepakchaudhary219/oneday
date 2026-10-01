package oneday.figures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import oneday.common.ApiException;
import oneday.identity.UserGuard;
import oneday.moments.MomentService;
import oneday.moments.MomentView;
import oneday.notify.Notice;
import oneday.notify.NotificationService;
import oneday.safety.BlockChecker;
import oneday.staff.StaffAudit;
import oneday.staff.StaffDirectory;
import oneday.staff.StaffRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public Figure accounts (v3; docs/07-v3-features.md). OneDay is built around people near you and people you
 * know; figures are the one deliberate exception, and they come with limits:
 *
 * <ul>
 * <li>Staff-verified (evidence reviewed by a moderator, every decision audited). A badge and a public
 * {@code @handle}; never a user id.</li>
 * <li>One-way follows. The follower count is shown to the figure only: no public metrics, no ranking.</li>
 * <li>Followers see the figure's live public stories in full, with no location. Following opens no message
 * channel: the usual Signal and Reveal rules still apply.</li>
 * </ul>
 */
@Service
public class PublicFigureService {

	private static final Pattern HANDLE = Pattern.compile("[a-z0-9_.]{3,30}");

	static final Duration REAPPLY_AFTER = Duration.ofDays(30);

	private final PublicFigureRepository figures;

	private final FigureFollowRepository follows;

	private final MomentService moments;

	private final NotificationService notifications;

	private final StaffDirectory staff;

	private final StaffAudit audit;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final Clock clock;

	public PublicFigureService(PublicFigureRepository figures, FigureFollowRepository follows, MomentService moments,
			NotificationService notifications, StaffDirectory staff, StaffAudit audit, BlockChecker blocks,
			UserGuard guard, Clock clock) {
		this.figures = figures;
		this.follows = follows;
		this.moments = moments;
		this.notifications = notifications;
		this.staff = staff;
		this.audit = audit;
		this.blocks = blocks;
		this.guard = guard;
		this.clock = clock;
	}

	@Transactional
	public MyFigureView apply(String userId, String rawHandle, String publicName, FigureCategory category, String bio,
			String evidence) {
		guard.requireContactAllowed(userId);
		String handle = rawHandle == null ? "" : rawHandle.strip().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
		if (!HANDLE.matcher(handle).matches()) {
			throw ApiException.badRequest("INVALID_HANDLE", "Handles are 3-30 of a-z, 0-9, _ and .");
		}
		if (publicName == null || publicName.isBlank() || publicName.strip().length() > 60 || category == null) {
			throw ApiException.badRequest("INVALID_APPLICATION", "A public name (up to 60) and a category are required");
		}
		if (evidence == null || evidence.isBlank() || evidence.length() > 500) {
			throw ApiException.badRequest("EVIDENCE_REQUIRED",
					"Tell us how to verify you: official links, press, a federation or party listing (up to 500)");
		}
		if (bio != null && bio.length() > 300) {
			throw ApiException.badRequest("BIO_TOO_LONG", "Bios are up to 300 characters");
		}
		Optional<PublicFigure> taken = figures.findByHandle(handle);
		if (taken.isPresent() && !taken.get().getUserId().equals(userId)) {
			throw ApiException.conflict("HANDLE_TAKEN", "That handle is taken");
		}
		PublicFigure figure = figures.findById(userId).orElseGet(() -> new PublicFigure(userId, clock.instant()));
		if (figure.getStatus() == PublicFigure.Status.APPROVED || figure.getStatus() == PublicFigure.Status.PENDING) {
			throw ApiException.conflict("ALREADY_APPLIED", "Your application is already " + figure.getStatus().name().toLowerCase(Locale.ROOT));
		}
		if (figure.getDecidedAt() != null && figure.getDecidedAt().plus(REAPPLY_AFTER).isAfter(clock.instant())) {
			throw ApiException.conflict("TOO_SOON", "You can apply again 30 days after a decision");
		}
		figure.apply(handle, publicName.strip(), category, bio == null || bio.isBlank() ? null : bio.strip(),
				evidence.strip(), clock.instant());
		figures.save(figure);
		return myView(figure);
	}

	@Transactional(readOnly = true)
	public Optional<MyFigureView> mine(String userId) {
		guard.requireExisting(userId);
		return figures.findById(userId).map(this::myView);
	}

	// ---- staff -------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ReviewItem> queue(String staffId) {
		staff.require(staffId, StaffRole.MODERATOR);
		return figures.findByStatusOrderByAppliedAt(PublicFigure.Status.PENDING)
			.stream()
			.map(f -> new ReviewItem(f.getUserId(), f.getHandle(), f.getPublicName(), f.getCategory(), f.getBio(),
					f.getEvidence(), f.getAppliedAt()))
			.toList();
	}

	@Transactional
	public MyFigureView decide(String staffId, String userId, Decision decision, String note) {
		staff.require(staffId, StaffRole.MODERATOR);
		if (staffId.equals(userId)) {
			throw ApiException.forbidden("OWN_APPLICATION", "Staff can't decide their own application");
		}
		PublicFigure figure = figures.findById(userId).orElseThrow(() -> ApiException.notFound("Application"));
		PublicFigure.Status next = switch (decision) {
			case APPROVE -> PublicFigure.Status.APPROVED;
			case REJECT -> PublicFigure.Status.REJECTED;
			case REVOKE -> PublicFigure.Status.REVOKED;
		};
		boolean valid = decision == Decision.REVOKE ? figure.isApproved()
				: figure.getStatus() == PublicFigure.Status.PENDING;
		if (!valid) {
			throw ApiException.conflict("INVALID_DECISION", "That decision doesn't apply to this application now");
		}
		figure.decide(next, staffId, clock.instant());
		audit.record(staffId, "FIGURE_" + decision.name(), "USER", userId, note);
		notifications.notice(userId, Notice.Kind.ACCOUNT, switch (decision) {
			case APPROVE -> "You're verified as a Public Figure: @" + figure.getHandle() + ".";
			case REJECT -> "We couldn't verify your Public Figure application. You can apply again in 30 days.";
			case REVOKE -> "Your Public Figure badge was removed. You can file a grievance if you disagree.";
		});
		return myView(figure);
	}

	// ---- following -----------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public FigureView profile(String viewerId, String handle) {
		PublicFigure figure = requireApproved(viewerId, handle);
		boolean following = follows.existsById(new FigureFollow.Key(viewerId, figure.getUserId()));
		return FigureView.of(figure, following);
	}

	@Transactional
	public FigureView follow(String viewerId, String handle) {
		guard.requireActive(viewerId);
		PublicFigure figure = requireApproved(viewerId, handle);
		if (figure.getUserId().equals(viewerId)) {
			throw ApiException.unprocessable("SELF_FOLLOW", "That's you");
		}
		FigureFollow.Key key = new FigureFollow.Key(viewerId, figure.getUserId());
		if (!follows.existsById(key)) {
			follows.save(new FigureFollow(viewerId, figure.getUserId(), clock.instant()));
		}
		return FigureView.of(figure, true);
	}

	@Transactional
	public void unfollow(String viewerId, String handle) {
		figures.findByHandle(normalize(handle))
			.ifPresent(f -> follows.deleteById(new FigureFollow.Key(viewerId, f.getUserId())));
	}

	@Transactional(readOnly = true)
	public List<FigureView> following(String viewerId) {
		guard.requireExisting(viewerId);
		Set<String> blocked = blocks.blockedEitherWay(viewerId);
		List<FigureView> out = new ArrayList<>();
		for (FigureFollow follow : follows.findByFollower(viewerId)) {
			figures.findById(follow.figureId())
				.filter(f -> f.isApproved() && !blocked.contains(f.getUserId()))
				.ifPresent(f -> out.add(FigureView.of(f, true)));
		}
		return out;
	}

	/** Live public stories of the figures you follow, newest first. */
	@Transactional(readOnly = true)
	public List<FeedItem> feed(String viewerId, int limit) {
		guard.requireActive(viewerId);
		Set<String> blocked = blocks.blockedEitherWay(viewerId);
		Map<String, PublicFigure> byUser = new HashMap<>();
		for (FigureFollow follow : follows.findByFollower(viewerId)) {
			figures.findById(follow.figureId())
				.filter(f -> f.isApproved() && !blocked.contains(f.getUserId()))
				.ifPresent(f -> byUser.put(f.getUserId(), f));
		}
		Set<String> reachable = guard.reachableAmong(byUser.keySet());
		List<FeedItem> items = new ArrayList<>();
		for (String figureId : reachable) {
			PublicFigure figure = byUser.get(figureId);
			moments.livePublicBy(Set.of(figureId), Map.of(figureId, figure.getPublicName()), limit)
				.forEach(m -> items.add(new FeedItem(figure.getHandle(), m)));
		}
		items.sort((a, b) -> b.moment().postedAt().compareTo(a.moment().postedAt()));
		return items.stream().limit(Math.clamp(limit, 1, 50)).toList();
	}

	@Transactional
	public void unfollowBetween(String a, String b) {
		follows.deleteBetween(a, b);
	}

	/** The approved figure's public identity, for other modules (AMA hosts). */
	@Transactional(readOnly = true)
	public Optional<FigureView> approved(String userId) {
		return figures.findById(userId).filter(PublicFigure::isApproved).map(f -> FigureView.of(f, false));
	}

	/** The figure behind a handle, for blocking or reporting from their profile. */
	@Transactional(readOnly = true)
	public Optional<String> figureBehind(String viewerId, String handle) {
		return figures.findByHandle(normalize(handle)).filter(PublicFigure::isApproved).map(PublicFigure::getUserId);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> export(String userId) {
		Map<String, Object> out = new HashMap<>();
		figures.findById(userId).ifPresent(f -> out.put("application", myView(f)));
		out.put("following", follows.findByFollower(userId)
			.stream()
			.map(f -> figures.findById(f.figureId()).map(PublicFigure::getHandle).orElse("(removed)"))
			.toList());
		return out;
	}

	@Transactional
	public void forget(String userId) {
		follows.deleteInvolving(userId);
		figures.deleteById(userId);
	}

	private PublicFigure requireApproved(String viewerId, String handle) {
		return figures.findByHandle(normalize(handle))
			.filter(PublicFigure::isApproved)
			.filter(f -> !blocks.isBlockedEitherWay(viewerId, f.getUserId()))
			.filter(f -> !guard.reachableAmong(List.of(f.getUserId())).isEmpty())
			.orElseThrow(() -> ApiException.notFound("Public figure"));
	}

	private static String normalize(String handle) {
		return handle == null ? "" : handle.strip().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
	}

	private MyFigureView myView(PublicFigure f) {
		return new MyFigureView(f.getHandle(), f.getPublicName(), f.getCategory(), f.getBio(), f.getStatus().name(),
				f.isApproved() ? follows.countFollowers(f.getUserId()) : null);
	}

	public enum Decision {
		APPROVE, REJECT, REVOKE
	}

	/** {@code followers}: only ever shown to the figure. */
	public record MyFigureView(String handle, String publicName, FigureCategory category, String bio, String status,
			Long followers) {
	}

	/** No follower count: there are no public metrics. */
	public record FigureView(String handle, String publicName, FigureCategory category, String bio, boolean verified,
			boolean following) {

		static FigureView of(PublicFigure f, boolean following) {
			return new FigureView(f.getHandle(), f.getPublicName(), f.getCategory(), f.getBio(), true, following);
		}
	}

	public record FeedItem(String handle, MomentView moment) {
	}

	public record ReviewItem(String userId, String handle, String publicName, FigureCategory category, String bio,
			String evidence, Instant appliedAt) {
	}
}
