package oneday.moments;

import java.time.Instant;

/**
 * One shape for both reveal layers. {@code AMBIENT} (Layer 0) carries only first name, activity and an
 * optional low-fi preview; caption and media are {@code null} until a Mutual Reveal (blueprint v2 §4).
 * Media is returned as short-lived URLs, never as storage keys.
 */
public record MomentView(
		String id,
		Layer layer,
		String firstName,
		MomentKind kind,
		String activityTag,
		boolean capturedLive,
		String previewUrl,
		String caption,
		String mediaUrl,
		ShareScope shareScope,
		Instant postedAt,
		boolean answersPrompt,
		String relayId,
		int relayPosition) {

	public enum Layer {
		AMBIENT, FULL
	}

	static MomentView full(Moment m, String firstName, String previewUrl, String mediaUrl) {
		return new MomentView(m.getId(), Layer.FULL, firstName, m.getKind(), m.getActivityTag(), m.isCapturedLive(),
				previewUrl, m.getCaption(), mediaUrl, m.getShareScope(), m.getCreatedAt(), m.getPromptKey() != null,
				m.getRelayRootId(), m.getRelayDepth());
	}

	static MomentView ambient(Moment m, String firstName, String previewUrl) {
		return new MomentView(m.getId(), Layer.AMBIENT, firstName, m.getKind(), m.getActivityTag(),
				m.isCapturedLive(), previewUrl, null, null, null, null, m.getPromptKey() != null, m.getRelayRootId(),
				m.getRelayDepth());
	}
}
