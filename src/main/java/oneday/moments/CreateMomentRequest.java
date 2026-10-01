package oneday.moments;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param capturedLive set by the client camera pipeline; M2 backs it with a signed capture attestation.
 * Absent means {@code false}.
 * @param previewAllowed lets a video show a silent low-fi preview at Layer 0. Absent means {@code false}.
 * @param promptKey answers Today's Prompt (the key from {@code GET /prompts/today})
 * @param replyToMomentId joins the Story Relay of that public moment; the new moment must be public too
 */
public record CreateMomentRequest(
		@NotNull MomentKind kind,
		@Size(max = 200) String caption,
		@Size(max = 30) String activityTag,
		@Size(max = 300) String mediaRef,
		@NotNull ShareScope shareScope,
		Boolean capturedLive,
		Boolean previewAllowed,
		@Size(max = 48) String promptKey,
		@Size(max = 36) String replyToMomentId) {

	public boolean isCapturedLive() {
		return Boolean.TRUE.equals(capturedLive);
	}

	public boolean isPreviewAllowed() {
		return Boolean.TRUE.equals(previewAllowed);
	}
}
