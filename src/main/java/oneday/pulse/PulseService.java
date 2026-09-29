package oneday.pulse;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import oneday.connections.ConnectionService;
import oneday.discovery.DiscoveryService;
import oneday.discovery.DiscoveryViews.HeatCell;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.safety.BlockChecker;
import oneday.signals.SignalService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Local Pulse (blueprint §33.2): one honest, predictable daily check-in at a time the user chose.
 * Counts are capped ("9+"); in Discretion Mode the headline never mentions signals or dating.
 */
@Service
public class PulseService {

	private final SignalService signals;

	private final ConnectionService connections;

	private final DiscoveryService discovery;

	private final LocationService locations;

	private final ProfileService profiles;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final Clock clock;

	public PulseService(SignalService signals, ConnectionService connections, DiscoveryService discovery,
			LocationService locations, ProfileService profiles, BlockChecker blocks, UserGuard guard, Clock clock) {
		this.signals = signals;
		this.connections = connections;
		this.discovery = discovery;
		this.locations = locations;
		this.profiles = profiles;
		this.blocks = blocks;
		this.guard = guard;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PulseView pulse(String userId) {
		guard.requireActive(userId);
		Profile profile = profiles.require(userId);
		long pending = signals.pendingCount(userId);
		String pendingLabel = pending > 9 ? "9+" : String.valueOf(pending);
		long newConnections = connections.countNewSince(userId, clock.instant().minus(Duration.ofDays(7)));
		List<NearbyActivity> nearby = locations.currentCell(userId)
			.map(cell -> discovery.heatAround(cell, blocks.blockedEitherWay(userId)))
			.orElse(List.of())
			.stream()
			.sorted(Comparator.comparing(HeatCell::level).reversed())
			.map(h -> new NearbyActivity(h.activity(), h.level().name()))
			.distinct()
			.limit(3)
			.toList();

		String headline;
		if (profile.isDiscretionMode()) {
			headline = "You have an update";
		}
		else if (pending > 0) {
			headline = pending == 1 ? "1 person sent you a signal" : pendingLabel + " people sent you signals";
		}
		else if (!nearby.isEmpty()) {
			headline = "Things are happening near you";
		}
		else {
			headline = "Quiet around you today. That's okay.";
		}
		return new PulseView(headline, pendingLabel, newConnections, nearby, profile.getPulseHour(),
				profile.isDiscretionMode());
	}

	public record NearbyActivity(String activity, String level) {
	}

	public record PulseView(String headline, String pendingSignals, long newConnectionsThisWeek,
			List<NearbyActivity> nearbyActivity, int digestHour, boolean discretionMode) {
	}
}
