package oneday.moments;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param capturedLive set by the client camera pipeline; M2 backs it with a signed capture attestation.
 * Absent means {@code false}.
 * @param previewAllowed lets a video show a silent low-fi preview at Layer 0. Absent means {@code false}.
 */
public record CreateMomentRequest(
		@NotNull MomentKind kind,
		@Size(max = 200) String caption,
		@Size(max = 30) String activityTag,
		@Size(max = 300) String mediaRef,
		@NotNull ShareScope shareScope,
		Boolean capturedLive,
		Boolean previewAllowed) {

	public boolean isCapturedLive() {
		return Boolean.TRUE.equals(capturedLive);
	}

	public boolean isPreviewAllowed() {
		return Boolean.TRUE.equals(previewAllowed);
	}
}
