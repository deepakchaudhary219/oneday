package oneday.profile;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Partial update: {@code null} fields are left unchanged. */
public record UpdateProfileRequest(
		@Size(min = 1, max = 40) String displayName,
		@Size(max = 280) String bio,
		@Size(max = 5) List<String> activities,
		@Size(max = 5) List<CoreValue> values,
		@Size(max = 6) List<String> languages,
		@Size(max = 8) String homeRegion,
		Gender gender,
		@Size(max = 3) List<Gender> interestedIn,
		AccountPrivacy accountPrivacy,
		Boolean datingLens,
		Boolean discretionMode,
		Integer discoveryRadiusKm,
		@Min(0) @Max(23) Integer pulseHour,
		@Size(max = 40) String timeZone,
		@Size(min = 2, max = 2) String country) {
}
