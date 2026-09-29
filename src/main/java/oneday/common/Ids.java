package oneday.common;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 identifiers: time-ordered (good InnoDB locality) and not enumerable, so internal ids can
 * safely appear in URLs.
 */
public final class Ids {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Ids() {
	}

	public static String newId() {
		long millis = System.currentTimeMillis();
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		for (int i = 0; i < 6; i++) {
			bytes[i] = (byte) (millis >>> (40 - 8 * i));
		}
		bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
		bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		return new UUID(buffer.getLong(), buffer.getLong()).toString();
	}
}
