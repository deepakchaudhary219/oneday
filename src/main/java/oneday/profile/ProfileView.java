package oneday.profile;

import java.util.Set;

import oneday.identity.User;
import oneday.identity.VerificationStatus;

/** The owner's own view of their profile; private fields are only ever returned to the owner. */
public record ProfileView(
		String displayName,
		String bio,
		Set<String> activities,
		Set<String> values,
		Set<String> languages,
		String homeRegion,
		Gender gender,
		Set<String> interestedIn,
		AccountPrivacy accountPrivacy,
		boolean datingLens,
		boolean discretionMode,
		int discoveryRadiusKm,
		int pulseHour,
		String timeZone,
		boolean safeZoneSet,
		VerificationStatus verificationStatus) {

	static ProfileView of(Profile p, User user) {
		return new ProfileView(p.getDisplayName(), p.getBio(), p.getActivities(), p.getValues(), p.getLanguages(),
				p.getHomeRegion(), p.getGender(), p.getInterestedIn(), p.getAccountPrivacy(), p.isDatingLens(),
				p.isDiscretionMode(), p.getDiscoveryRadiusKm(), p.getPulseHour(), p.getTimeZone(),
				p.getSafeZonePrefix() != null,
				user.getVerificationStatus());
	}
}
