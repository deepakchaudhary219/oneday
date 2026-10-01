package oneday.discovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.config.OneDayProperties;
import oneday.connections.ConnectionService;
import oneday.geo.GeoCell;
import oneday.geo.GeoMath;
import oneday.geo.Geohash;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.moments.Moment;
import oneday.moments.MomentKind;
import oneday.moments.MomentService;
import oneday.profile.ActivityTags;
import oneday.profile.Affinity;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.prompts.PromptCatalog;
import oneday.safety.BlockChecker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Story Map (blueprint v2 §6, docs/05-engagement-psychology.md §3.1): public stories placed where they
 * were captured, so the city reads like a living map of what people are doing right now.
 *
 * <p>
 * Unlike a pin map, no single person is ever placed. Stories are grouped by <b>adaptive k-anonymous
 * clustering</b>: a ~0.7 km² neighbourhood cell becomes a cluster only when at least {@code neighbourhoodK}
 * different people posted there; otherwise its stories roll up into the ~5 km area, which needs
 * {@code areaK} people; whatever is left goes to an unplaced "around your city" shelf. Stories posted inside
 * the owner's Safe Zone always go to that shelf. Wider scopes (city, Roots, language) never place below
 * area level: privacy scales inversely with reach.
 *
 * <p>
 * Lenses make it personal rather than infinite: Roots (people from your home region), language, activity,
 * and Today's Prompt. Story Relays are drawn as threads between clusters. Every story is a Layer-0 card;
 * the only way closer is a Signal and a Mutual Reveal.
 */
@Service
public class StoryMapService {

	/** A cluster is "live now" if its newest story is this fresh. */
	static final Duration LIVE_NOW = Duration.ofMinutes(30);

	/** At most this many stories per person on the map, so one person can't flood an area. */
	static final int MAX_STORIES_PER_PERSON = 3;

	static final String CITY_SHELF = "city";

	private final MomentService moments;

	private final LocationService locations;

	private final ProfileService profiles;

	private final ConnectionService connections;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final PromptCatalog prompts;

	private final RateLimiter rateLimiter;

	private final StoryMapProperties settings;

	private final OneDayProperties.Discovery discovery;

	private final Clock clock;

	public StoryMapService(MomentService moments, LocationService locations, ProfileService profiles,
			ConnectionService connections, BlockChecker blocks, UserGuard guard, PromptCatalog prompts,
			RateLimiter rateLimiter, StoryMapProperties settings, OneDayProperties properties, Clock clock) {
		this.moments = moments;
		this.locations = locations;
		this.profiles = profiles;
		this.connections = connections;
		this.blocks = blocks;
		this.guard = guard;
		this.prompts = prompts;
		this.rateLimiter = rateLimiter;
		this.settings = settings;
		this.discovery = properties.discovery();
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public StoryMap map(String viewerId, DiscoveryScope scope, String activityFilter, boolean todaysPrompt) {
		guard.requireActive(viewerId);
		if (!rateLimiter.tryAcquire("map:" + viewerId, discovery.queriesPerHour(), Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("MAP_RATE_LIMITED", "Take a breather; the map refreshes soon");
		}
		if (!connections.inCouple(List.of(viewerId)).isEmpty()) {
			return StoryMap.empty(scope, "Couple Mode is on, so the Story Map is paused for you both.");
		}
		Profile viewer = profiles.require(viewerId);
		if (scope == DiscoveryScope.ROOTS && viewer.getHomeRegion() == null) {
			throw ApiException.conflict("HOME_REGION_REQUIRED", "Add your home region to see stories from home");
		}
		if (scope == DiscoveryScope.LANGUAGE && viewer.getLanguages().isEmpty()) {
			throw ApiException.conflict("LANGUAGES_REQUIRED", "Add the languages you speak first");
		}
		GeoCell here = locations.requireCurrentCell(viewerId);
		double radiusKm = scope == DiscoveryScope.RADIUS
				? Math.min(viewer.getDiscoveryRadiusKm(), discovery.maxRadiusKm()) : discovery.cityRadiusKm();
		boolean fineAllowed = scope == DiscoveryScope.RADIUS;
		String activity = ActivityTags.normalize(activityFilter);
		String promptKey = todaysPrompt ? prompts.todayFor(viewerId).key() : null;
		Instant now = clock.instant();

		// 1. Candidate stories: live, public, within reach, by people the viewer may see.
		Set<String> blocked = blocks.blockedEitherWay(viewerId);
		Map<String, List<Moment>> byOwner = new LinkedHashMap<>();
		for (Moment m : moments.livePublicNear(here, radiusKm + 1)) {
			String owner = m.getOwnerId();
			if (owner.equals(viewerId) || blocked.contains(owner) || m.getCell() == null) {
				continue;
			}
			if (promptKey != null && !promptKey.equals(m.getPromptKey())) {
				continue;
			}
			GeoCell captured = GeoCell.of(m.getCell());
			if (GeoMath.haversineKm(here.lat(), here.lon(), captured.lat(), captured.lon()) > radiusKm) {
				continue;
			}
			List<Moment> list = byOwner.computeIfAbsent(owner, o -> new ArrayList<>());
			if (list.size() < MAX_STORIES_PER_PERSON) {
				list.add(m);
			}
		}
		Set<String> visible = new HashSet<>(guard.reachableAmong(byOwner.keySet()));
		visible.retainAll(locations.currentCells(visible).keySet());
		visible.removeAll(connections.inCouple(visible));
		Map<String, Profile> owners = visible.stream().collect(Collectors.toMap(Function.identity(), profiles::require));
		List<Story> stories = new ArrayList<>();
		for (String ownerId : visible) {
			Profile owner = owners.get(ownerId);
			if (!matchesScope(scope, viewer, owner)) {
				continue;
			}
			for (Moment m : byOwner.get(ownerId)) {
				if (activity == null || activity.equals(m.getActivityTag()) || owner.getActivities().contains(activity)) {
					stories.add(new Story(m, owner, Affinity.between(viewer, owner), owner.isInSafeZone(m.getCell())));
				}
			}
		}
		if (stories.isEmpty()) {
			return StoryMap.empty(scope, todaysPrompt ? "No answers near you yet. Yours could be the first."
					: "Quiet on the map right now. Post a moment and put your area on it ✨");
		}

		// 2. Adaptive k-anonymous clustering: neighbourhood → area → unplaced city shelf.
		Map<String, String> clusterOf = new HashMap<>();
		Map<String, List<Story>> clusters = new LinkedHashMap<>();
		List<Story> pending = new ArrayList<>();
		Map<String, List<Story>> byCell = group(stories.stream().filter(s -> !s.inSafeZone()).toList(),
				s -> s.moment().getCell());
		for (Map.Entry<String, List<Story>> cell : byCell.entrySet()) {
			if (fineAllowed && distinctOwners(cell.getValue()) >= settings.neighbourhoodK()) {
				clusters.put(cell.getKey(), cell.getValue());
			}
			else {
				pending.addAll(cell.getValue());
			}
		}
		List<Story> shelf = new ArrayList<>(stories.stream().filter(Story::inSafeZone).toList());
		for (Map.Entry<String, List<Story>> area : group(pending,
				s -> s.moment().getCell().substring(0, Geohash.AREA_PRECISION)).entrySet()) {
			if (distinctOwners(area.getValue()) >= settings.areaK()) {
				clusters.put(area.getKey(), area.getValue());
			}
			else {
				shelf.addAll(area.getValue());
			}
		}
		clusters.forEach((id, list) -> list.forEach(s -> clusterOf.put(s.moment().getId(), id)));

		// 3. Relay threads between placed clusters.
		Set<String> relayIds = new LinkedHashSet<>();
		stories.forEach(s -> relayIds.add(s.moment().getRelayRootId() != null ? s.moment().getRelayRootId() : s.moment().getId()));
		Map<String, Long> relayAnswers = moments.relayCounts(relayIds);
		List<RelayThread> threads = threads(stories, clusterOf, relayAnswers);

		// Most relevant first: shared roots, values and activities, then how many people are there.
		List<Cluster> placed = clusters.entrySet()
			.stream()
			.sorted(Comparator.comparingInt((Map.Entry<String, List<Story>> e) -> relevance(e.getValue())).reversed())
			.limit(settings.maxClusters())
			.map(e -> cluster(e.getKey(), e.getKey().length() > Geohash.AREA_PRECISION ? Level.NEIGHBOURHOOD : Level.AREA,
					e.getValue(), relayAnswers, now))
			.toList();
		Cluster cityShelf = shelf.isEmpty() ? null : cluster(CITY_SHELF, Level.CITY, shelf, relayAnswers, now);
		return new StoryMap(scope, placed, cityShelf, threads, null);
	}

	private Cluster cluster(String id, Level level, List<Story> stories, Map<String, Long> relayAnswers, Instant now) {
		List<Story> ordered = stories.stream()
			.sorted(Comparator.comparingInt((Story s) -> s.affinity().rank())
				.reversed()
				.thenComparing(s -> s.moment().getCreatedAt(), Comparator.reverseOrder()))
			.toList();
		Map<String, Long> activityCounts = stories.stream()
			.filter(s -> s.moment().getActivityTag() != null)
			.collect(Collectors.groupingBy(s -> s.moment().getActivityTag(), LinkedHashMap::new, Collectors.counting()));
		List<String> topActivities = activityCounts.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
			.limit(3)
			.map(Map.Entry::getKey)
			.toList();
		long people = distinctOwners(stories);
		long fromHome = stories.stream().filter(s -> s.affinity().rootsMatch()).map(s -> s.owner().getUserId()).distinct().count();
		boolean liveNow = stories.stream().anyMatch(s -> s.moment().getCreatedAt().isAfter(now.minus(LIVE_NOW)));
		Double lat = null;
		Double lon = null;
		if (level != Level.CITY) {
			double[] center = Geohash.center(id);
			lat = center[0];
			lon = center[1];
		}
		List<StoryCard> cards = ordered.stream().limit(settings.storiesPerCluster()).map(s -> {
			Moment m = s.moment();
			String relayId = m.getRelayRootId() != null ? m.getRelayRootId() : m.getId();
			long relayLength = relayAnswers.getOrDefault(relayId, 0L);
			return new StoryCard(m.getId(), s.owner().firstName(), m.getKind(), m.getActivityTag(), moments.previewUrl(m),
					m.isCapturedLive(), s.affinity().rootsMatch() ? s.owner().getHomeRegion() : null,
					s.affinity().sharedLanguages(), m.getPromptKey() != null,
					relayLength > 0 ? relayId : null, relayLength > 0 ? (int) relayLength + 1 : 0,
					s.affinity().explanation(m.getActivityTag()));
		}).toList();
		return new Cluster(id, level, lat, lon, cap(people), topActivities.isEmpty() ? null : topActivities.get(0),
				topActivities, (int) Math.min(fromHome, 9), liveNow, cards, Math.max(0, stories.size() - cards.size()));
	}

	private static int relevance(List<Story> stories) {
		return stories.stream().mapToInt(s -> s.affinity().rank()).sum() + (int) distinctOwners(stories);
	}

	private static List<RelayThread> threads(List<Story> stories, Map<String, String> clusterOf,
			Map<String, Long> relayAnswers) {
		Map<String, List<Story>> byRelay = group(stories,
				s -> s.moment().getRelayRootId() != null ? s.moment().getRelayRootId() : s.moment().getId());
		List<RelayThread> threads = new ArrayList<>();
		byRelay.forEach((relayId, members) -> {
			List<String> path = members.stream()
				.sorted(Comparator.comparingInt(s -> s.moment().getRelayDepth()))
				.map(s -> clusterOf.get(s.moment().getId()))
				.filter(c -> c != null)
				.distinct()
				.toList();
			if (path.size() >= 2) {
				threads.add(new RelayThread(relayId, path, (int) (relayAnswers.getOrDefault(relayId, 0L) + 1)));
			}
		});
		threads.sort(Comparator.comparingInt(RelayThread::length).reversed());
		return threads.stream().limit(20).toList();
	}

	private static <K> Map<K, List<Story>> group(List<Story> stories, Function<Story, K> key) {
		return stories.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
	}

	private static long distinctOwners(List<Story> stories) {
		return stories.stream().map(s -> s.owner().getUserId()).distinct().count();
	}

	private static String cap(long n) {
		return n > 9 ? "9+" : String.valueOf(n);
	}

	private static boolean matchesScope(DiscoveryScope scope, Profile viewer, Profile owner) {
		return switch (scope) {
			case RADIUS, CITY -> true;
			case ROOTS -> viewer.getHomeRegion().equals(owner.getHomeRegion());
			case LANGUAGE -> owner.getLanguages().stream().anyMatch(viewer.getLanguages()::contains);
		};
	}

	private record Story(Moment moment, Profile owner, Affinity affinity, boolean inSafeZone) {
	}

	public enum Level {
		/** A ~0.7 km² cell where at least k people posted (radius scope only). */
		NEIGHBOURHOOD,
		/** A ~5 km area where at least k people posted. */
		AREA,
		/** Unplaced: "around your city". */
		CITY
	}

	/**
	 * A group of stories on the map. Coordinates are the cell or area centre, never a story's own position;
	 * {@code people} and {@code fromYourHomeRegion} are capped.
	 */
	public record Cluster(String id, Level level, Double centerLat, Double centerLon, String people, String vibe,
			List<String> activities, int fromYourHomeRegion, boolean liveNow, List<StoryCard> stories, int more) {
	}

	/** A Layer-0 story card. {@code momentId} opens it (Layer 0) or receives a Signal. */
	public record StoryCard(String momentId, String firstName, MomentKind kind, String activity, String previewUrl,
			boolean liveCaptured, String sharedHomeRegion, Set<String> sharedLanguages, boolean answersPrompt,
			String relayId, int relayLength, String whyYouSeeThis) {
	}

	/** A Story Relay drawn across the map: the clusters it passes through, in order. */
	public record RelayThread(String relayId, List<String> clusterIds, int length) {
	}

	public record StoryMap(DiscoveryScope scope, List<Cluster> clusters, Cluster aroundYourCity,
			List<RelayThread> relays, String message) {

		static StoryMap empty(DiscoveryScope scope, String message) {
			return new StoryMap(scope, List.of(), null, List.of(), message);
		}
	}
}
