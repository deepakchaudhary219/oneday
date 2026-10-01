package oneday.dates;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import oneday.common.ApiException;
import oneday.geo.GeoCell;
import oneday.geo.GeoMath;
import oneday.geo.LocationService;
import oneday.identity.UserGuard;
import oneday.staff.StaffAudit;
import oneday.staff.StaffDirectory;
import oneday.staff.StaffRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safety-Verified Meeting Points (blueprint v2 §5.3): Trust & Safety curates public venues; people planning
 * a meet-up see the ones around them. Distances are measured from the viewer's own snapped cell.
 */
@Service
public class MeetingPointService {

	static final int MAX_RADIUS_KM = 15;

	private static final int MAX_RESULTS = 20;

	private final MeetingPointRepository points;

	private final LocationService locations;

	private final UserGuard guard;

	private final StaffDirectory staff;

	private final StaffAudit audit;

	private final Clock clock;

	public MeetingPointService(MeetingPointRepository points, LocationService locations, UserGuard guard,
			StaffDirectory staff, StaffAudit audit, Clock clock) {
		this.points = points;
		this.locations = locations;
		this.guard = guard;
		this.staff = staff;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<MeetingPointView> nearby(String userId, MeetingPointCategory category, int radiusKm) {
		guard.requireActive(userId);
		GeoCell here = locations.requireCurrentCell(userId);
		double radius = Math.clamp(radiusKm, 1, MAX_RADIUS_KM);
		double[] box = GeoMath.boundingBoxDegrees(here.lat(), radius);
		return points.findActiveInBox(here.lat() - box[0], here.lat() + box[0], here.lon() - box[1], here.lon() + box[1])
			.stream()
			.filter(p -> category == null || p.getCategory() == category)
			.map(p -> MeetingPointView.of(p, GeoMath.haversineKm(here.lat(), here.lon(), p.getLat(), p.getLon())))
			.filter(v -> v.distanceKm() <= radius)
			.sorted(Comparator.comparingDouble(MeetingPointView::distanceKm))
			.limit(MAX_RESULTS)
			.toList();
	}

	@Transactional(readOnly = true)
	public Optional<MeetingPoint> findActive(String id) {
		return points.findById(id).filter(MeetingPoint::isActive);
	}

	@Transactional(readOnly = true)
	public Optional<MeetingPoint> find(String id) {
		return id == null ? Optional.empty() : points.findById(id);
	}

	@Transactional
	public MeetingPointView create(String staffId, String name, MeetingPointCategory category, String address,
			double lat, double lon, String safetyNotes) {
		staff.require(staffId, StaffRole.MODERATOR);
		if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
			throw ApiException.badRequest("INVALID_COORDINATES", "Latitude or longitude out of range");
		}
		MeetingPoint point = points.save(new MeetingPoint(name.strip(), category, address.strip(), lat, lon,
				safetyNotes == null || safetyNotes.isBlank() ? null : safetyNotes.strip(), staffId, clock.instant()));
		audit.record(staffId, "MEETING_POINT_VERIFIED", "MEETING_POINT", point.getId(), point.getName());
		return MeetingPointView.of(point, null);
	}

	@Transactional
	public void deactivate(String staffId, String id, String note) {
		staff.require(staffId, StaffRole.MODERATOR);
		MeetingPoint point = points.findById(id).orElseThrow(() -> ApiException.notFound("Meeting point"));
		point.deactivate();
		audit.record(staffId, "MEETING_POINT_REMOVED", "MEETING_POINT", id, note);
	}

	/** A public venue. {@code distanceKm} is from the viewer's cell, to one decimal. */
	public record MeetingPointView(String id, String name, MeetingPointCategory category, String address, double lat,
			double lon, String safetyNotes, Double distanceKm) {

		static MeetingPointView of(MeetingPoint p, Double distanceKm) {
			return new MeetingPointView(p.getId(), p.getName(), p.getCategory(), p.getAddress(), p.getLat(), p.getLon(),
					p.getSafetyNotes(), distanceKm == null ? null : Math.round(distanceKm * 10) / 10.0);
		}
	}
}
