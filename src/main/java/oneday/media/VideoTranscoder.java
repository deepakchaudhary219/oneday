package oneday.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transcodes videos with ffmpeg. Every output is written with {@code -map_metadata -1} (drops container
 * metadata such as the GPS {@code location} atom, device make/model and creation time) and no chapters or
 * data streams. It also writes the silent, low-resolution, 4-second Layer-0 preview. The worker image
 * must include ffmpeg; without it, video uploads are refused rather than served unprocessed.
 */
@Component
class VideoTranscoder {

	private static final Logger log = LoggerFactory.getLogger(VideoTranscoder.class);

	private static final long TIMEOUT_SECONDS = 180;

	private final String ffmpeg;

	private final String ffprobe;

	private final boolean available;

	VideoTranscoder(MediaProperties properties) {
		this.ffmpeg = properties.ffmpegPath();
		this.ffprobe = properties.ffprobePath();
		this.available = probe();
		if (!available) {
			log.warn("ffmpeg/ffprobe not found ({} / {}); video uploads are disabled", ffmpeg, ffprobe);
		}
	}

	boolean isAvailable() {
		return available;
	}

	double durationSeconds(Path input) throws IOException, MediaRejectedException {
		String output = run(List.of(ffprobe, "-v", "error", "-show_entries", "format=duration", "-of",
				"default=noprint_wrappers=1:nokey=1", input.toString()));
		try {
			return Double.parseDouble(output.trim().lines().findFirst().orElse(""));
		}
		catch (NumberFormatException ex) {
			throw new MediaRejectedException("That file isn't a video we can read");
		}
	}

	void transcode(Path input, Path output) throws IOException, MediaRejectedException {
		run(List.of(ffmpeg, "-nostdin", "-y", "-i", input.toString(), "-map_metadata", "-1", "-map_chapters", "-1",
				"-map", "0:v:0", "-map", "0:a:0?", "-dn", "-sn", "-c:v", "libx264", "-preset", "veryfast", "-crf", "26",
				"-vf", "scale='min(1080,iw)':-2", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k", "-movflags",
				"+faststart", output.toString()));
	}

	/** Silent, 360 px wide, at most 4 seconds: enough to convey a moment, not to replace it. */
	void preview(Path input, Path output) throws IOException, MediaRejectedException {
		run(List.of(ffmpeg, "-nostdin", "-y", "-i", input.toString(), "-map_metadata", "-1", "-map_chapters", "-1",
				"-map", "0:v:0", "-an", "-dn", "-sn", "-t", "4", "-vf", "scale=360:-2", "-c:v", "libx264", "-preset",
				"veryfast", "-crf", "32", "-pix_fmt", "yuv420p", "-movflags", "+faststart", output.toString()));
	}

	private String run(List<String> command) throws IOException, MediaRejectedException {
		Path log = Files.createTempFile("oneday-ffmpeg-", ".log");
		try {
			Process process = new ProcessBuilder(new ArrayList<>(command)).redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();
			if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new IOException("ffmpeg timed out");
			}
			String output = Files.readString(log, StandardCharsets.UTF_8);
			if (process.exitValue() != 0) {
				// ffmpeg exits non-zero for undecodable input: a property of the file, not a transient fault.
				throw new MediaRejectedException("That video is damaged or in an unsupported format");
			}
			return output;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while transcoding", ex);
		}
		finally {
			Files.deleteIfExists(log);
		}
	}

	private boolean probe() {
		try {
			for (String binary : List.of(ffmpeg, ffprobe)) {
				Process p = new ProcessBuilder(binary, "-version").redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
				if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) {
					return false;
				}
			}
			return true;
		}
		catch (IOException ex) {
			return false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
