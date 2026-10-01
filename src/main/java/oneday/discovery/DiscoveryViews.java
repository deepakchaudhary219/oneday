package oneday.discovery;

import java.util.List;
import java.util.Set;

import oneday.geo.Direction;
import oneday.geo.DistanceBand;
import oneday.moments.MomentKind;

/** Response shapes for discovery. No internal user id, coordinate or score ever appears here. */
public final class DiscoveryViews {

	private DiscoveryViews() {
	}

	/**
	 * One Layer-0 ambient node. {@code nodeId} is the moment id, the handle for sending a Signal.
	 * {@code sharedHomeRegion} is present only when it matches the viewer's own region.
	 */
	public record ConstellationNode(
			String nodeId,
			String firstName,
			MomentKind kind,
			String activity,
			DistanceBand band,
			String distance,
			Direction direction,
			boolean liveCaptured,
			String previewUrl,
			String sharedHomeRegion,
			Set<String> sharedLanguages,
			String whyYouSeeThis) {
	}

	public record Constellation(DiscoveryScope scope, int page, List<ConstellationNode> nodes, boolean caughtUp,
			String message) {
	}

	public enum HeatLevel {
		LOW, ACTIVE, BUSY
	}

	/** An aggregate over a ~5 km cell, emitted only when at least k distinct people contribute. */
	public record HeatCell(String area, double centerLat, double centerLon, String activity, HeatLevel level) {
	}
}
