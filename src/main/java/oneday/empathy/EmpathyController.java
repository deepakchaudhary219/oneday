package oneday.empathy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Empathy Mirror lexicon for the app. In end-to-end encrypted chats the server can't read messages, so the
 * same check runs on the sender's device before encryption, with the same list and the same normalisation
 * rules (documented in {@link LexiconToneClassifier}). Versioned by ETag so the app only downloads changes.
 */
@RestController
class EmpathyController {

	private final byte[] lexicon;

	private final String etag;

	EmpathyController(@Value("${oneday.empathy.lexicon:classpath:empathy/lexicon.txt}") Resource source) {
		try (InputStream in = source.getInputStream()) {
			this.lexicon = in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		this.etag = "\"" + HexFormat.of().formatHex(sha256(lexicon), 0, 16) + "\"";
	}

	@GetMapping("/empathy/lexicon")
	ResponseEntity<byte[]> lexicon(@RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(304).eTag(etag).build();
		}
		return ResponseEntity.ok()
			.eTag(etag)
			.cacheControl(CacheControl.noCache())
			.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
			.body(lexicon);
	}

	private static byte[] sha256(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
