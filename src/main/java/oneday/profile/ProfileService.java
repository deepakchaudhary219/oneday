package oneday.profile;

import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import oneday.common.ApiException;
import oneday.config.OneDayProperties;
import oneday.consent.ConsentLedger;
import oneday.consent.ConsentPurpose;
import oneday.identity.User;
import oneday.identity.UserGuard;
import oneday.plus.Entitlements;

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

	private final Entitlements entitlements;

	private final ConsentLedger consents;

	public ProfileService(ProfileRepository profiles, UserGuard guard, Clock clock, OneDayProperties properties,
			Entitlements entitlements, ConsentLedger consents) {
		this.entitlements = entitlements;
		this.consents = consents;
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

	/** For data export: also works for suspended accounts, which keep their data rights. */
	@Transactional(readOnly = true)
	public ProfileView exportView(String userId) {
		return ProfileView.of(require(userId), guard.requireExisting(userId));
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
		affirmConsents(userId, request);
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
			int max = Math.min(properties.discovery().maxRadiusKm(), entitlements.maxRadiusKm(userId));
			if (request.discoveryRadiusKm() < 1 || request.discoveryRadiusKm() > max) {
				throw ApiException.badRequest("INVALID_RADIUS", "Discovery radius can be 1 to " + max + " km"
						+ (entitlements.isPlus(userId) ? "" : " (Plus widens it)"));
			}
			profile.setDiscoveryRadiusKm(request.discoveryRadiusKm());
		}
		if (request.pulseHour() != null) {
			profile.setPulseHour(request.pulseHour());
		}
		if (request.timeZone() != null) {
			try {
				profile.setTimeZone(java.time.ZoneId.of(request.timeZone().trim()).getId());
			}
			catch (java.time.DateTimeException ex) {
				throw ApiException.badRequest("INVALID_TIME_ZONE", "Use a time zone such as Asia/Kolkata");
			}
		}
		if (request.country() != null) {
			String country = request.country().trim().toUpperCase(Locale.ROOT);
			if (!Arrays.asList(Locale.getISOCountries()).contains(country)) {
				throw ApiException.badRequest("INVALID_COUNTRY", "Use a two-letter country code such as IN or US");
			}
			profile.setCountryCode(country);
		}
		profile.touch(clock.instant());
		return ProfileView.of(profile, user);
	}

	/** The distinct time zones people use (a small set: one per region, not per person). */
	@Transactional(readOnly = true)
	public List<String> timeZonesInUse() {
		return profiles.findTimeZonesInUse();
	}

	/** DPDP withdrawal of {@code DATING_PREFERENCES}: the lens goes off and the private fields are deleted. */
	@Transactional
	public void clearDatingPreferences(String userId) {
		profiles.findById(userId).ifPresent(profile -> {
			profile.setDatingLens(false);
			profile.setGender(null);
			profile.setInterestedIn(new LinkedHashSet<>());
			profile.touch(clock.instant());
		});
	}

	/** DPDP withdrawal of {@code ROOTS_AND_LANGUAGES}. */
	@Transactional
	public void clearRootsAndLanguages(String userId) {
		profiles.findById(userId).ifPresent(profile -> {
			profile.setHomeRegion(null);
			profile.setLanguages(new LinkedHashSet<>());
			profile.touch(clock.instant());
		});
	}

	@Transactional
	public void delete(String userId) {
		profiles.deleteById(userId);
	}

	/** Providing purpose-bound fields is the affirmative action that records consent (DPDP s.6(1)). */
	private void affirmConsents(String userId, UpdateProfileRequest request) {
		if (Boolean.TRUE.equals(request.datingLens()) || request.gender() != null
				|| (request.interestedIn() != null && !request.interestedIn().isEmpty())) {
			consents.affirm(userId, ConsentPurpose.DATING_PREFERENCES);
		}
		if ((request.homeRegion() != null && !request.homeRegion().isBlank())
				|| (request.languages() != null && !request.languages().isEmpty())) {
			consents.affirm(userId, ConsentPurpose.ROOTS_AND_LANGUAGES);
		}
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
