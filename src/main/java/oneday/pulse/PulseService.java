package oneday.pulse;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import oneday.connections.ConnectionService;
import oneday.discovery.DiscoveryService;
import oneday.discovery.DiscoveryViews.HeatCell;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.ledger.WeeklyRecap;
import oneday.moments.Moment;
import oneday.moments.MomentService;
import oneday.profile.Profile;
import oneday.profile.ProfileService;
import oneday.prompts.PromptService;
import oneday.prompts.PromptService.TodayView;
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

	private final PromptService prompts;

	private final MomentService moments;

	private final WeeklyRecap recap;

	public PulseService(SignalService signals, ConnectionService connections, DiscoveryService discovery,
			LocationService locations, ProfileService profiles, BlockChecker blocks, UserGuard guard, Clock clock,
			PromptService prompts, MomentService moments, WeeklyRecap recap) {
		this.recap = recap;
		this.prompts = prompts;
		this.moments = moments;
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

		// Anticipation with substance: the day's prompt and real answers to your own moments. All capped.
		TodayView today = prompts.today(userId);
		long relayAnswers = moments.relayCounts(moments.liveBy(userId).stream().map(Moment::getId).toList())
			.values()
			.stream()
			.mapToLong(Long::longValue)
			.sum();
		String relayLabel = relayAnswers > 9 ? "9+" : String.valueOf(relayAnswers);

		String headline;
		if (profile.isDiscretionMode()) {
			headline = "You have an update";
		}
		else if (pending > 0) {
			headline = pending == 1 ? "1 person sent you a signal" : pendingLabel + " people sent you signals";
		}
		else if (relayAnswers > 0) {
			headline = "Your moment started a relay: " + relayLabel
					+ (relayAnswers == 1 ? " person answered" : " people answered");
		}
		else if (!today.answeredByYou() && !"0".equals(today.answeredNearby())) {
			headline = today.answeredNearby() + " near you answered today's prompt: " + today.text();
		}
		else if (!nearby.isEmpty()) {
			headline = "Things are happening near you";
		}
		else {
			headline = "Quiet around you today. That's okay.";
		}
		// Mondays (local): last week's private recap is ready to open, if there was anything in it.
		boolean monday = LocalDate.now(clock.withZone(ZoneId.of(profile.getTimeZone()))).getDayOfWeek() == DayOfWeek.MONDAY;
		boolean recapReady = monday && recap.lastWeek(userId).ready();
		return new PulseView(headline, pendingLabel, newConnections, nearby, profile.getPulseHour(),
				profile.isDiscretionMode(), today.text(), today.answeredNearby(), today.answeredByYou(), relayLabel,
				recapReady);
	}

	public record NearbyActivity(String activity, String level) {
	}

	/**
	 * @param todaysPrompt the day's prompt; {@code promptAnsweredNearby} is capped at "9+"
	 * @param relayAnswers answers to the viewer's own live moments' relays, capped at "9+"
	 */
	public record PulseView(String headline, String pendingSignals, long newConnectionsThisWeek,
			List<NearbyActivity> nearbyActivity, int digestHour, boolean discretionMode, String todaysPrompt,
			String promptAnsweredNearby, boolean promptAnsweredByYou, String relayAnswers, boolean weeklyRecapReady) {
	}
}
