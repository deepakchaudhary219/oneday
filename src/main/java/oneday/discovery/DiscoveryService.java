package oneday.discovery;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.config.OneDayProperties;
import oneday.connections.ConnectionService;
import oneday.discovery.DiscoveryViews.Constellation;
import oneday.discovery.DiscoveryViews.ConstellationNode;
import oneday.discovery.DiscoveryViews.HeatCell;
import oneday.discovery.DiscoveryViews.HeatLevel;
import oneday.geo.GeoCell;
import oneday.geo.Geohash;
import oneday.geo.LocationPrivacy;
import oneday.geo.LocationPrivacy.Placement;
import oneday.geo.LocationPrivacy.Precision;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.moments.Moment;
import oneday.moments.MomentService;
import oneday.profile.ActivityTags;
import oneday.profile.Affinity;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;
import oneday.signals.SignalService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Constellation and Heat layer (blueprint v2 §6). Discovery is bounded: a few small pages per
 * session, then an explicit "caught up" stop instead of an infinite feed (blueprint §22.6).
 */
@Service
public class DiscoveryService {

	private final MomentService moments;

	private final LocationService locations;

	private final ProfileService profiles;

	private final ConnectionService connections;

	private final SignalService signals;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final LocationPrivacy privacy;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	private final OneDayProperties.Discovery settings;

	public DiscoveryService(MomentService moments, LocationService locations, ProfileService profiles,
			ConnectionService connections,
			SignalService signals, BlockChecker blocks, UserGuard guard, LocationPrivacy privacy,
			RateLimiter rateLimiter, Clock clock, OneDayProperties properties) {
		this.moments = moments;
		this.locations = locations;
		this.profiles = profiles;
		this.connections = connections;
		this.signals = signals;
		this.blocks = blocks;
		this.guard = guard;
		this.privacy = privacy;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.settings = properties.discovery();
	}

	@Transactional(readOnly = true)
	public Constellation constellation(String viewerId, DiscoveryScope scope, String activityFilter, int page) {
		guard.requireActive(viewerId);
		if (!rateLimiter.tryAcquire("discover:" + viewerId, settings.queriesPerHour(), Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("DISCOVERY_RATE_LIMITED", "Take a breather; discovery refreshes soon");
		}
		if (page < 0 || page >= settings.maxPagesPerSession()) {
			return caughtUp(scope, page);
		}
		Profile viewer = profiles.require(viewerId);
		if (scope == DiscoveryScope.ROOTS && viewer.getHomeRegion() == null) {
			throw ApiException.conflict("HOME_REGION_REQUIRED", "Add your home region to meet people from home");
		}
		if (scope == DiscoveryScope.LANGUAGE && viewer.getLanguages().isEmpty()) {
			throw ApiException.conflict("LANGUAGES_REQUIRED", "Add the languages you speak first");
		}
		GeoCell here = locations.requireCurrentCell(viewerId);
		double radiusKm = scope == DiscoveryScope.RADIUS
				? Math.min(viewer.getDiscoveryRadiusKm(), settings.maxRadiusKm()) : settings.cityRadiusKm();
		Precision precision = scope == DiscoveryScope.RADIUS ? Precision.BAND : Precision.CITY;
		String activity = ActivityTags.normalize(activityFilter);

		Set<String> excludedPeople = new HashSet<>(blocks.blockedEitherWay(viewerId));
		excludedPeople.addAll(connections.connectedUserIds(viewerId));
		excludedPeople.add(viewerId);
		excludedPeople.addAll(signals.recentlySignalledBy(viewerId));

		// Newest public moment per person; people, not posts, are what the constellation shows.
		Map<String, Moment> latestByOwner = new LinkedHashMap<>();
		for (Moment m : moments.livePublicNear(here, radiusKm + 1)) {
			if (!excludedPeople.contains(m.getOwnerId())) {
				latestByOwner.putIfAbsent(m.getOwnerId(), m);
			}
		}
		Set<String> reachable = guard.reachableAmong(latestByOwner.keySet());
		Map<String, GeoCell> ownerCells = locations.currentCells(reachable);

		List<Candidate> candidates = new ArrayList<>();
		for (Moment moment : latestByOwner.values()) {
			GeoCell ownerCell = ownerCells.get(moment.getOwnerId());
			if (ownerCell == null) {
				continue; // paused location or not reachable: out of discovery immediately
			}
			Profile owner = profiles.require(moment.getOwnerId());
			if (!matchesScope(scope, viewer, owner) || !matchesActivity(activity, moment, owner)) {
				continue;
			}
			Optional<Placement> placement = privacy.place(here, ownerCell, viewerId, owner.getUserId(), precision,
					owner.isInSafeZone(ownerCell.cell()), radiusKm);
			placement.ifPresent(p -> candidates.add(new Candidate(moment, owner, p, Affinity.between(viewer, owner))));
		}
		candidates.sort(Comparator.comparingInt((Candidate c) -> c.affinity().rank())
			.reversed()
			.thenComparing(c -> c.moment().getCreatedAt(), Comparator.reverseOrder()));

		int from = page * settings.batchSize();
		List<ConstellationNode> nodes = candidates.stream()
			.skip(from)
			.limit(settings.batchSize())
			.map(DiscoveryService::toNode)
			.toList();
		boolean caughtUp = from + settings.batchSize() >= candidates.size() || page + 1 >= settings.maxPagesPerSession();
		return new Constellation(scope, page, nodes, caughtUp, caughtUp ? caughtUpMessage() : null);
	}

	/** Aggregated activity by ~5 km area, emitted only when at least k distinct people contribute. */
	@Transactional(readOnly = true)
	public List<HeatCell> heat(String viewerId) {
		guard.requireActive(viewerId);
		GeoCell here = locations.requireCurrentCell(viewerId);
		return heatAround(here, blocks.blockedEitherWay(viewerId));
	}

	public List<HeatCell> heatAround(GeoCell here, Set<String> excludedOwners) {
		int k = settings.heatKAnonymity();
		Map<String, Map<String, Set<String>>> ownersByAreaAndActivity = new LinkedHashMap<>();
		for (Moment m : moments.livePublicNear(here, settings.cityRadiusKm())) {
			if (m.getActivityTag() == null || m.getCell() == null || excludedOwners.contains(m.getOwnerId())) {
				continue;
			}
			ownersByAreaAndActivity
				.computeIfAbsent(m.getCell().substring(0, Geohash.AREA_PRECISION), a -> new LinkedHashMap<>())
				.computeIfAbsent(m.getActivityTag(), t -> new HashSet<>())
				.add(m.getOwnerId());
		}
		List<HeatCell> cells = new ArrayList<>();
		ownersByAreaAndActivity.forEach((area, byActivity) -> byActivity.forEach((activity, owners) -> {
			if (owners.size() >= k) {
				GeoCell center = GeoCell.of(area);
				HeatLevel level = owners.size() >= 6 * k ? HeatLevel.BUSY
						: owners.size() >= 3 * k ? HeatLevel.ACTIVE : HeatLevel.LOW;
				cells.add(new HeatCell(area, center.lat(), center.lon(), activity, level));
			}
		}));
		return cells;
	}

	private static boolean matchesScope(DiscoveryScope scope, Profile viewer, Profile owner) {
		return switch (scope) {
			case RADIUS, CITY -> true;
			case ROOTS -> viewer.getHomeRegion().equals(owner.getHomeRegion());
			case LANGUAGE -> owner.getLanguages().stream().anyMatch(viewer.getLanguages()::contains);
		};
	}

	private static boolean matchesActivity(String activity, Moment moment, Profile owner) {
		return activity == null || activity.equals(moment.getActivityTag()) || owner.getActivities().contains(activity);
	}

	private static ConstellationNode toNode(Candidate c) {
		Moment m = c.moment();
		String activity = m.getActivityTag() != null ? m.getActivityTag()
				: c.owner().getActivities().stream().findFirst().orElse(null);
		return new ConstellationNode(m.getId(), c.owner().firstName(), m.getKind(), activity, c.placement().band(),
				c.placement().band().label(), c.placement().direction(), m.isCapturedLive(), m.previewRef(),
				c.affinity().sharedHomeRegion(), c.affinity().sharedLanguages(),
				c.affinity().explanation(m.getActivityTag()));
	}

	private Constellation caughtUp(DiscoveryScope scope, int page) {
		return new Constellation(scope, page, List.of(), true, caughtUpMessage());
	}

	private static String caughtUpMessage() {
		return "You're caught up for now. Go do something real ✨";
	}

	private record Candidate(Moment moment, Profile owner, Placement placement, Affinity affinity) {
	}
}
