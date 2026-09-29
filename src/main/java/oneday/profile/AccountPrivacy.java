package oneday.profile;

/**
 * Whether the profile is browsable by default (blueprint v2 §3). Independent from each post's share
 * scope: a PRIVATE account can still make a single public share.
 */
public enum AccountPrivacy {
	PUBLIC, PRIVATE
}
