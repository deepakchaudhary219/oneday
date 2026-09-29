package oneday.moments;

import java.time.Instant;

/**
 * One shape for both reveal layers. {@code AMBIENT} (Layer 0) carries only first name, activity and an
 * optional low-fi preview; caption and media are {@code null} until a Mutual Reveal (blueprint v2 §4).
 */
public record MomentView(
		String id,
		Layer layer,
		String firstName,
		MomentKind kind,
		String activityTag,
		boolean capturedLive,
		String previewRef,
		String caption,
		String mediaRef,
		ShareScope shareScope,
		Instant postedAt) {

	public enum Layer {
		AMBIENT, FULL
	}

	static MomentView full(Moment m, String firstName) {
		return new MomentView(m.getId(), Layer.FULL, firstName, m.getKind(), m.getActivityTag(), m.isCapturedLive(),
				m.previewRef(), m.getCaption(), m.getMediaRef(), m.getShareScope(), m.getCreatedAt());
	}

	static MomentView ambient(Moment m, String firstName) {
		return new MomentView(m.getId(), Layer.AMBIENT, firstName, m.getKind(), m.getActivityTag(),
				m.isCapturedLive(), m.previewRef(), null, null, null, null);
	}
}
