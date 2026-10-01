package oneday.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/** Real media files for tests, including files carrying the metadata the worker must strip. */
public final class TestMedia {

	/** A location-like secret planted in metadata; it must never survive processing. */
	public static final String SECRET = "GPS 12.97160N 77.59460E Koramangala";

	private TestMedia() {
	}

	public static byte[] jpeg(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.ORANGE);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, width / 2, height / 4);
		g.dispose();
		return encode(image, "jpeg");
	}

	public static byte[] png(int width, int height) {
		return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png");
	}

	/** A 1-bit PNG with a huge pixel count but a tiny file size (decompression-bomb shape). */
	public static byte[] pixelBombPng(int width, int height) {
		return encode(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png");
	}

	/**
	 * A JPEG carrying an EXIF APP1 segment with the given orientation tag, a second APP1 segment and a
	 * comment segment that both contain {@link #SECRET}.
	 */
	public static byte[] jpegWithMetadata(int width, int height, int orientation) {
		byte[] plain = jpeg(width, height);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(plain, 0, 2); // SOI
		out.writeBytes(segment(0xFFE1, exifOrientation(orientation)));
		out.writeBytes(segment(0xFFE1, ("Exif\0\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1)));
		out.writeBytes(segment(0xFFFE, SECRET.getBytes(StandardCharsets.ISO_8859_1)));
		out.write(plain, 2, plain.length - 2);
		return out.toByteArray();
	}

	public static boolean ffmpegInstalled() {
		try {
			return new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start()
				.waitFor(10, TimeUnit.SECONDS);
		}
		catch (IOException | InterruptedException ex) {
			return false;
		}
	}

	/** A short MP4 (test pattern + tone) whose container carries {@link #SECRET} as location and title. */
	public static byte[] mp4WithMetadata(int seconds) {
		try {
			Path file = Files.createTempFile("oneday-test-", ".mp4");
			Process process = new ProcessBuilder("ffmpeg", "-nostdin", "-y", "-f", "lavfi", "-i",
					"testsrc=size=320x240:rate=15", "-f", "lavfi", "-i", "sine=frequency=440", "-t",
					String.valueOf(seconds), "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a",
					"aac", "-metadata", "location=+12.9716+077.5946/", "-metadata", "title=" + SECRET, "-metadata",
					"com.apple.quicktime.location.ISO6709=+12.9716+077.5946/", file.toString())
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
			if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
				throw new IllegalStateException("ffmpeg could not create the test video");
			}
			byte[] bytes = Files.readAllBytes(file);
			Files.delete(file);
			return bytes;
		}
		catch (IOException | InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Runs ffprobe on bytes and returns its JSON description of format and streams. */
	public static String ffprobe(byte[] media) {
		try {
			Path file = Files.createTempFile("oneday-probe-", ".mp4");
			Files.write(file, media);
			Path out = Files.createTempFile("oneday-probe-", ".json");
			Process process = new ProcessBuilder("ffprobe", "-v", "error", "-show_format", "-show_streams", "-of",
					"json", file.toString())
				.redirectOutput(out.toFile())
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			process.waitFor(30, TimeUnit.SECONDS);
			String json = Files.readString(out);
			Files.delete(file);
			Files.delete(out);
			return json;
		}
		catch (IOException | InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static BufferedImage decode(byte[] bytes) {
		try {
			return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static byte[] exifOrientation(int orientation) {
		ByteBuffer tiff = ByteBuffer.allocate(6 + 8 + 2 + 12 + 4);
		tiff.put("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
		tiff.put(new byte[] { 'M', 'M', 0, 42 }).putInt(8); // big-endian TIFF header, IFD0 at offset 8
		tiff.putShort((short) 1); // one entry
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
		tiff.putInt(0); // no next IFD
		return tiff.array();
	}

	private static byte[] segment(int marker, byte[] payload) {
		ByteBuffer buf = ByteBuffer.allocate(4 + payload.length);
		buf.putShort((short) marker).putShort((short) (payload.length + 2)).put(payload);
		return buf.array();
	}

	private static byte[] encode(BufferedImage image, String format) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(image, format, out)) {
				throw new IllegalStateException("No writer for " + format);
			}
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
