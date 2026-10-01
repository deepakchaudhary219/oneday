package oneday.chat;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Message franking (as in Facebook's "Messenger Secret Conversations" paper): the sender commits to
 * {@code HMAC-SHA256(k, plaintext)} with a fresh random key {@code k} that travels inside the ciphertext. The
 * server stores the commitment next to the sender and time. A recipient who reports the message reveals the
 * plaintext and {@code k}; a match proves the reported person sent exactly that text, without the server ever
 * being able to read messages that nobody reports.
 */
final class Franking {

	private Franking() {
	}

	static boolean verify(byte[] commitment, byte[] key, String plaintext) {
		if (commitment == null || key == null || key.length < 16 || key.length > 64 || plaintext == null) {
			return false;
		}
		try {
			Mac hmac = Mac.getInstance("HmacSHA256");
			hmac.init(new SecretKeySpec(key, "HmacSHA256"));
			return MessageDigest.isEqual(commitment, hmac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			return false;
		}
	}
}
