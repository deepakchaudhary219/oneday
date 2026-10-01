package oneday.profile;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import oneday.common.StringSetConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profiles")
public class Profile {

	@Id
	private String userId;

	@Column(nullable = false)
	private String displayName;

	private String bio;

	/** Concrete things the person is up for right now (blueprint §25.5), normalised lowercase. */
	@Convert(converter = StringSetConverter.class)
	private Set<String> activities = new LinkedHashSet<>();

	/** {@link CoreValue} names. */
	@Convert(converter = StringSetConverter.class)
	@Column(name = "core_values")
	private Set<String> values = new LinkedHashSet<>();

	/** ISO 639 codes, lowercase. */
	@Convert(converter = StringSetConverter.class)
	private Set<String> languages = new LinkedHashSet<>();

	/** Region-level only (ISO 3166-2 style, e.g. IN-KL), never a town or address. Powers Roots. */
	private String homeRegion;

	@Enumerated(EnumType.STRING)
	private Gender gender;

	/** {@link Gender} names; private, see {@link Gender}. */
	@Convert(converter = StringSetConverter.class)
	private Set<String> interestedIn = new LinkedHashSet<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountPrivacy accountPrivacy;

	/** Private "open to dating" switch (blueprint v2 §5.1); never shown to anyone else. */
	@Column(nullable = false)
	private boolean datingLens;

	/** Neutral notification copy for shared phones (blueprint v2 §9). */
	@Column(nullable = false)
	private boolean discretionMode;

	@Column(nullable = false)
	private int discoveryRadiusKm;

	/** Hour of day (user's choice, never randomised) for the Local Pulse digest. */
	@Column(nullable = false)
	private int pulseHour;

	/** IANA zone for the Local Pulse hour (the pilot city's zone by default). */
	@Column(nullable = false)
	private String timeZone;

	/** ISO 3166-1 alpha-2; picks the emergency number shown on safety surfaces. */
	@Column(nullable = false)
	private String countryCode;

	/** Geohash-5 prefix; while inside it the user appears only as "nearby area" (blueprint v2 §6.3). */
	private String safeZonePrefix;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Profile() {
	}

	public Profile(String userId, String displayName, int discoveryRadiusKm, Instant now) {
		this.userId = userId;
		this.displayName = displayName;
		this.accountPrivacy = AccountPrivacy.PUBLIC;
		this.discoveryRadiusKm = discoveryRadiusKm;
		this.pulseHour = 19;
		this.timeZone = "Asia/Kolkata";
		this.countryCode = "IN";
		this.updatedAt = now;
	}

	public String firstName() {
		String trimmed = displayName.trim();
		int space = trimmed.indexOf(' ');
		return space > 0 ? trimmed.substring(0, space) : trimmed;
	}

	public boolean isInSafeZone(String cell) {
		return safeZonePrefix != null && cell != null && cell.startsWith(safeZonePrefix);
	}

	/** True when both people are open to dating and each matches the other's stated interest. */
	public boolean datingCompatibleWith(Profile other) {
		return datingLens && other.datingLens && gender != null && other.gender != null
				&& interestedIn.contains(other.gender.name()) && other.interestedIn.contains(gender.name());
	}

	public String getUserId() {
		return userId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getBio() {
		return bio;
	}

	public void setBio(String bio) {
		this.bio = bio;
	}

	public Set<String> getActivities() {
		return activities;
	}

	public void setActivities(Set<String> activities) {
		this.activities = activities;
	}

	public Set<String> getValues() {
		return values;
	}

	public void setValues(Set<String> values) {
		this.values = values;
	}

	public Set<String> getLanguages() {
		return languages;
	}

	public void setLanguages(Set<String> languages) {
		this.languages = languages;
	}

	public String getHomeRegion() {
		return homeRegion;
	}

	public void setHomeRegion(String homeRegion) {
		this.homeRegion = homeRegion;
	}

	public Gender getGender() {
		return gender;
	}

	public void setGender(Gender gender) {
		this.gender = gender;
	}

	public Set<String> getInterestedIn() {
		return interestedIn;
	}

	public void setInterestedIn(Set<String> interestedIn) {
		this.interestedIn = interestedIn;
	}

	public AccountPrivacy getAccountPrivacy() {
		return accountPrivacy;
	}

	public void setAccountPrivacy(AccountPrivacy accountPrivacy) {
		this.accountPrivacy = accountPrivacy;
	}

	public boolean isDatingLens() {
		return datingLens;
	}

	public void setDatingLens(boolean datingLens) {
		this.datingLens = datingLens;
	}

	public boolean isDiscretionMode() {
		return discretionMode;
	}

	public void setDiscretionMode(boolean discretionMode) {
		this.discretionMode = discretionMode;
	}

	public int getDiscoveryRadiusKm() {
		return discoveryRadiusKm;
	}

	public void setDiscoveryRadiusKm(int discoveryRadiusKm) {
		this.discoveryRadiusKm = discoveryRadiusKm;
	}

	public int getPulseHour() {
		return pulseHour;
	}

	public void setPulseHour(int pulseHour) {
		this.pulseHour = pulseHour;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String emergencyNumber() {
		return EmergencyNumbers.forCountry(countryCode);
	}

	public String getSafeZonePrefix() {
		return safeZonePrefix;
	}

	public void setSafeZonePrefix(String safeZonePrefix) {
		this.safeZonePrefix = safeZonePrefix;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void touch(Instant now) {
		this.updatedAt = now;
	}
}
