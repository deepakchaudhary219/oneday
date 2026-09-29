package oneday.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

/**
 * Re-encodes photos so nothing but pixels survives. Decoding ignores metadata, and the output JPEG is
 * written with no metadata at all, so EXIF (including GPS), XMP, IPTC, comments and thumbnails are all
 * dropped. EXIF orientation is applied to the pixels first, since the tag itself is removed.
 */
@Component
class PhotoProcessor {

	static final int MAX_EDGE = 2048;

	private static final Set<String> FORMATS = Set.of("jpeg", "png");

	/** @throws MediaRejectedException when the file is not an acceptable image */
	void process(Path input, Path output, long maxPixels) throws IOException, MediaRejectedException {
		int orientation = exifOrientation(input);
		BufferedImage decoded;
		try (ImageInputStream in = ImageIO.createImageInputStream(input.toFile())) {
			Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
			if (readers == null || !readers.hasNext()) {
				throw new MediaRejectedException("That file isn't a photo we can read");
			}
			ImageReader reader = readers.next();
			try {
				if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
					throw new MediaRejectedException("Only JPEG and PNG photos are supported");
				}
				reader.setInput(in, true, true);
				long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
				if (pixels > maxPixels) {
					throw new MediaRejectedException("That photo has too many pixels");
				}
				decoded = reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
		catch (javax.imageio.IIOException ex) {
			throw new MediaRejectedException("That photo is damaged or unreadable");
		}
		writeJpeg(normalize(decoded, orientation), output);
	}

	/** Applies orientation, flattens transparency onto white, and caps the longest edge. */
	private static BufferedImage normalize(BufferedImage source, int orientation) {
		boolean swap = orientation >= 5 && orientation <= 8;
		int srcW = source.getWidth();
		int srcH = source.getHeight();
		int w = swap ? srcH : srcW;
		int h = swap ? srcW : srcH;
		double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(w, h));
		int outW = Math.max(1, (int) Math.round(w * scale));
		int outH = Math.max(1, (int) Math.round(h * scale));

		AffineTransform transform = new AffineTransform();
		transform.scale(scale, scale);
		transform.concatenate(orientationTransform(orientation, srcW, srcH));

		BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, outW, outH);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(source, transform, null);
		}
		finally {
			g.dispose();
		}
		return out;
	}

	/** Maps EXIF orientation 1–8 to the transform that displays the image upright. */
	static AffineTransform orientationTransform(int orientation, int w, int h) {
		AffineTransform t = new AffineTransform();
		switch (orientation) {
			case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
			case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
			case 4 -> { t.translate(0, h); t.scale(1, -1); }
			case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
			case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
			case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
			case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
			default -> { }
		}
		return t;
	}

	private static void writeJpeg(BufferedImage image, Path output) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(0.85f);
		try (ImageOutputStream out = ImageIO.createImageOutputStream(Files.newOutputStream(output))) {
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
	}

	/** Reads the EXIF orientation tag (0x0112) from a JPEG's APP1 segment; 1 (upright) if absent. */
	static int exifOrientation(Path jpeg) {
		try (InputStream in = Files.newInputStream(jpeg)) {
			byte[] head = in.readNBytes(128 * 1024);
			ByteBuffer buf = ByteBuffer.wrap(head);
			if (head.length < 4 || (buf.getShort(0) & 0xFFFF) != 0xFFD8) {
				return 1;
			}
			int pos = 2;
			while (pos + 4 <= head.length) {
				int marker = buf.getShort(pos) & 0xFFFF;
				int length = buf.getShort(pos + 2) & 0xFFFF;
				if ((marker & 0xFF00) != 0xFF00 || marker == 0xFFDA || length < 2) {
					return 1;
				}
				if (marker == 0xFFE1 && pos + 10 <= head.length
						&& new String(head, pos + 4, 6, java.nio.charset.StandardCharsets.ISO_8859_1).equals("Exif\0\0")) {
					return orientationFromTiff(head, pos + 10, Math.min(head.length, pos + 2 + length));
				}
				pos += 2 + length;
			}
		}
		catch (IOException | RuntimeException ex) {
			// Malformed metadata never blocks processing; the photo is just not rotated.
		}
		return 1;
	}

	private static int orientationFromTiff(byte[] data, int tiff, int end) {
		ByteBuffer buf = ByteBuffer.wrap(data, 0, end);
		buf.order(data[tiff] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
		int ifd = tiff + buf.getInt(tiff + 4);
		int entries = buf.getShort(ifd) & 0xFFFF;
		for (int i = 0; i < entries; i++) {
			int entry = ifd + 2 + i * 12;
			if ((buf.getShort(entry) & 0xFFFF) == 0x0112) {
				int value = buf.getShort(entry + 8) & 0xFFFF;
				return value >= 1 && value <= 8 ? value : 1;
			}
		}
		return 1;
	}
}
