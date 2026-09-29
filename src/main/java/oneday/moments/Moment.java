package oneday.moments;

import java.time.Instant;

import oneday.common.Ids;
import oneday.geo.GeoCell;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A Story. Public shares carry the capture cell (Proof-of-Presence, blueprint §35.2), never coordinates. */
@Entity
@Table(name = "moments")
public class Moment {

	@Id
	private String id;

	@Column(nullable = false)
	private String ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MomentKind kind;

	private String caption;

	private String activityTag;

	private String mediaRef;

	/** Whether a silent low-fi preview may be shown at Layer 0 (video only). */
	private boolean previewAllowed;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ShareScope shareScope;

	private boolean capturedLive;

	private String cell;

	private Double cellLat;

	private Double cellLon;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	protected Moment() {
	}

	Moment(String ownerId, MomentKind kind, String caption, String activityTag, String mediaRef,
			boolean previewAllowed, ShareScope shareScope, boolean capturedLive, GeoCell cell, Instant now,
			Instant expiresAt) {
		this.id = Ids.newId();
		this.ownerId = ownerId;
		this.kind = kind;
		this.caption = caption;
		this.activityTag = activityTag;
		this.mediaRef = mediaRef;
		this.previewAllowed = previewAllowed;
		this.shareScope = shareScope;
		this.capturedLive = capturedLive;
		if (cell != null) {
			this.cell = cell.cell();
			this.cellLat = cell.lat();
			this.cellLon = cell.lon();
		}
		this.createdAt = now;
		this.expiresAt = expiresAt;
	}

	public boolean isLive(Instant now) {
		return expiresAt.isAfter(now);
	}

	public boolean isPublic() {
		return shareScope == ShareScope.PUBLIC_DISCOVERY;
	}

	/** Layer-0 preview reference: only for video, only if the poster allowed it. */
	public String previewRef() {
		return kind == MomentKind.VIDEO && previewAllowed && mediaRef != null ? mediaRef + "?rendition=preview-lowfi"
				: null;
	}

	public String getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public MomentKind getKind() {
		return kind;
	}

	public String getCaption() {
		return caption;
	}

	public String getActivityTag() {
		return activityTag;
	}

	public String getMediaRef() {
		return mediaRef;
	}

	public boolean isPreviewAllowed() {
		return previewAllowed;
	}

	public ShareScope getShareScope() {
		return shareScope;
	}

	public boolean isCapturedLive() {
		return capturedLive;
	}

	public String getCell() {
		return cell;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
