package oneday.pulsestatus;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Music on a status is a Spotify track, shown through Spotify's official embed (blueprint v2 §8). The app reads
 * <i>Now Playing</i> on the phone with the person's own Spotify session; OneDay never holds Spotify tokens and
 * stores only the public track id.
 */
public final class Music {

	private static final Pattern TRACK = Pattern.compile(
			"^(?:https://open\\.spotify\\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?track/|spotify:track:)([A-Za-z0-9]{22})(?:\\?.*)?$");

	private Music() {
	}

	/** The track id of a Spotify track link or URI, if it is one. */
	static Optional<String> spotifyTrackId(String link) {
		Matcher m = TRACK.matcher(link.strip());
		return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
	}

	static View view(String trackId, String title) {
		return trackId == null ? null
				: new View("SPOTIFY", trackId, title, "https://open.spotify.com/embed/track/" + trackId,
						"https://open.spotify.com/track/" + trackId);
	}

	/** {@code title} is what the person's Spotify app reported; the embed is authoritative. */
	public record View(String provider, String trackId, String title, String embedUrl, String openUrl) {
	}
}
