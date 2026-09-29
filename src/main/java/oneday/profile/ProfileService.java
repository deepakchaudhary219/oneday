package oneday.profile;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import oneday.common.ApiException;
import oneday.config.OneDayProperties;
import oneday.identity.User;
import oneday.identity.UserGuard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

	private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");

	private static final Pattern REGION = Pattern.compile("[A-Z]{2}-[A-Z0-9]{1,3}");

	private final ProfileRepository profiles;

	private final UserGuard guard;

	private final Clock clock;

	private final OneDayProperties properties;

	public ProfileService(ProfileRepository profiles, UserGuard guard, Clock clock, OneDayProperties properties) {
		this.profiles = profiles;
		this.guard = guard;
		this.clock = clock;
		this.properties = properties;
	}

	public Profile create(String userId, String displayName) {
		int defaultRadius = Math.min(5, properties.discovery().maxRadiusKm());
		return profiles.save(new Profile(userId, displayName.trim(), defaultRadius, clock.instant()));
	}

	@Transactional(readOnly = true)
	public Optional<Profile> find(String userId) {
		return profiles.findById(userId);
	}

	public Profile require(String userId) {
		return profiles.findById(userId).orElseThrow(() -> ApiException.notFound("Profile"));
	}

	@Transactional(readOnly = true)
	public ProfileView me(String userId) {
		User user = guard.requireActive(userId);
		return ProfileView.of(require(userId), user);
	}

	@Transactional
	public ProfileView update(String userId, UpdateProfileRequest request) {
		User user = guard.requireActive(userId);
		Profile profile = require(userId);
		if (request.displayName() != null) {
			profile.setDisplayName(request.displayName().trim());
		}
		if (request.bio() != null) {
			profile.setBio(request.bio().isBlank() ? null : request.bio().trim());
		}
		if (request.activities() != null) {
			Set<String> activities = new LinkedHashSet<>();
			request.activities().forEach(a -> {
				String tag = ActivityTags.normalize(a);
				if (tag != null) {
					activities.add(tag);
				}
			});
			profile.setActivities(activities);
		}
		if (request.values() != null) {
			Set<String> values = new LinkedHashSet<>();
			request.values().forEach(v -> values.add(v.name()));
			profile.setValues(values);
		}
		if (request.languages() != null) {
			profile.setLanguages(normalizeLanguages(request.languages()));
		}
		if (request.homeRegion() != null) {
			profile.setHomeRegion(normalizeRegion(request.homeRegion()));
		}
		if (request.gender() != null) {
			profile.setGender(request.gender());
		}
		if (request.interestedIn() != null) {
			Set<String> interested = new LinkedHashSet<>();
			request.interestedIn().forEach(g -> interested.add(g.name()));
			profile.setInterestedIn(interested);
		}
		if (request.accountPrivacy() != null) {
			profile.setAccountPrivacy(request.accountPrivacy());
		}
		if (request.datingLens() != null) {
			profile.setDatingLens(request.datingLens());
		}
		if (request.discretionMode() != null) {
			profile.setDiscretionMode(request.discretionMode());
		}
		if (request.discoveryRadiusKm() != null) {
			int max = properties.discovery().maxRadiusKm();
			if (request.discoveryRadiusKm() < 1 || request.discoveryRadiusKm() > max) {
				throw ApiException.badRequest("INVALID_RADIUS", "Discovery radius must be between 1 and " + max + " km");
			}
			profile.setDiscoveryRadiusKm(request.discoveryRadiusKm());
		}
		if (request.pulseHour() != null) {
			profile.setPulseHour(request.pulseHour());
		}
		profile.touch(clock.instant());
		return ProfileView.of(profile, user);
	}

	@Transactional
	public void delete(String userId) {
		profiles.deleteById(userId);
	}

	private static Set<String> normalizeLanguages(List<String> raw) {
		Set<String> languages = new LinkedHashSet<>();
		for (String language : raw) {
			String code = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
			if (!LANGUAGE.matcher(code).matches()) {
				throw ApiException.badRequest("INVALID_LANGUAGE", "Languages are ISO 639 codes such as 'hi' or 'kn'");
			}
			languages.add(code);
		}
		return languages;
	}

	private static String normalizeRegion(String raw) {
		if (raw.isBlank()) {
			return null;
		}
		String region = raw.trim().toUpperCase(Locale.ROOT);
		if (!REGION.matcher(region).matches()) {
			throw ApiException.badRequest("INVALID_REGION",
					"Home region is region-level only, e.g. 'IN-KL' (Kerala) or 'IN-WB' (West Bengal)");
		}
		return region;
	}
}
