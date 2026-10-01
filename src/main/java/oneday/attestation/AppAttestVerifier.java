package oneday.attestation;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * Verifies Apple App Attest objects as Apple specifies ("Validating apps that connect to your server"). Pure
 * logic with no I/O, so every rule is unit-testable against a stand-in CA.
 *
 * <p>
 * <b>Attestation</b> (once per device key): the certificate chain ends at Apple's App Attestation root; the
 * leaf carries {@code SHA256(authData ‖ clientDataHash)} in extension {@value #NONCE_OID}; the key id is the
 * SHA-256 of the leaf's public key; the RP id is our App ID; the counter is 0; the AAGUID names the environment;
 * and the credential id is the key id.
 * <p>
 * <b>Assertion</b> (per request): the stored key signed {@code SHA256(authenticatorData ‖ clientDataHash)}, the RP
 * id is our App ID, and the counter moved forward.
 */
class AppAttestVerifier {

	static final String NONCE_OID = "1.2.840.113635.100.8.2";

	static final byte[] AAGUID_PRODUCTION = Arrays.copyOf("appattest".getBytes(StandardCharsets.US_ASCII), 16);

	static final byte[] AAGUID_DEVELOPMENT = "appattestdevelop".getBytes(StandardCharsets.US_ASCII);

	private final CBORMapper cbor = CBORMapper.builder().build();

	private final X509Certificate root;

	private final byte[] appIdHash;

	private final boolean allowDevelopment;

	private final Clock clock;

	/** @param appId {@code <Team ID>.<bundle id>} */
	AppAttestVerifier(X509Certificate root, String appId, boolean allowDevelopment, Clock clock) {
		this.root = root;
		this.appIdHash = sha256(appId.getBytes(StandardCharsets.UTF_8));
		this.allowDevelopment = allowDevelopment;
		this.clock = clock;
	}

	/** Returns the verified key, or throws {@link Rejected} naming the rule that failed. */
	RegisteredKey verifyAttestation(byte[] keyId, byte[] attestationObject, byte[] clientDataHash) {
		JsonNode attestation = read(attestationObject);
		require("apple-appattest".equals(attestation.path("fmt").asString()), "format");
		List<X509Certificate> chain = new ArrayList<>();
		for (JsonNode der : attestation.path("attStmt").path("x5c")) {
			chain.add(certificate(bytes(der)));
		}
		require(chain.size() >= 2, "certificate chain");
		validateChain(chain);
		X509Certificate credential = chain.get(0);

		byte[] authData = bytes(attestation.path("authData"));
		require(authData != null && authData.length >= 55, "authenticator data");
		byte[] nonce = sha256(concat(authData, clientDataHash));
		require(MessageDigest.isEqual(nonce, nonceExtension(credential)), "nonce");

		byte[] publicKeyPoint = uncompressedPoint((ECPublicKey) credential.getPublicKey());
		require(MessageDigest.isEqual(sha256(publicKeyPoint), keyId), "key id");
		require(MessageDigest.isEqual(Arrays.copyOfRange(authData, 0, 32), appIdHash), "app id");
		require(counter(authData) == 0, "counter");
		byte[] aaguid = Arrays.copyOfRange(authData, 37, 53);
		String environment;
		if (Arrays.equals(aaguid, AAGUID_PRODUCTION)) {
			environment = "production";
		}
		else {
			require(allowDevelopment && Arrays.equals(aaguid, AAGUID_DEVELOPMENT), "environment");
			environment = "development";
		}
		int credentialIdLength = ((authData[53] & 0xFF) << 8) | (authData[54] & 0xFF);
		require(authData.length >= 55 + credentialIdLength, "credential id");
		require(MessageDigest.isEqual(Arrays.copyOfRange(authData, 55, 55 + credentialIdLength), keyId), "credential id");
		return new RegisteredKey(credential.getPublicKey().getEncoded(), environment);
	}

	/** Returns the assertion's counter, which the caller must check is greater than the stored one. */
	long verifyAssertion(byte[] publicKey, byte[] assertionObject, byte[] clientDataHash) {
		JsonNode assertion = read(assertionObject);
		byte[] authenticatorData = bytes(assertion.path("authenticatorData"));
		byte[] signature = bytes(assertion.path("signature"));
		require(authenticatorData != null && authenticatorData.length >= 37 && signature != null, "assertion");
		byte[] nonce = sha256(concat(authenticatorData, clientDataHash));
		try {
			PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(publicKey));
			Signature ecdsa = Signature.getInstance("SHA256withECDSA");
			ecdsa.initVerify(key);
			ecdsa.update(nonce);
			require(ecdsa.verify(signature), "signature");
		}
		catch (GeneralSecurityException ex) {
			throw new Rejected("signature");
		}
		require(MessageDigest.isEqual(Arrays.copyOfRange(authenticatorData, 0, 32), appIdHash), "app id");
		return counter(authenticatorData);
	}

	private void validateChain(List<X509Certificate> chain) {
		try {
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
			parameters.setRevocationEnabled(false); // Apple publishes no revocation for these short-lived leaves
			parameters.setDate(Date.from(clock.instant()));
			CertPathValidator.getInstance("PKIX").validate(factory.generateCertPath(chain), parameters);
		}
		catch (GeneralSecurityException ex) {
			throw new Rejected("certificate chain");
		}
	}

	/** Extension value: OCTET STRING { SEQUENCE { [1] EXPLICIT OCTET STRING nonce } }. */
	private static byte[] nonceExtension(X509Certificate credential) {
		byte[] value = credential.getExtensionValue(NONCE_OID);
		require(value != null, "nonce");
		Der outer = Der.read(value, 0, 0x04);
		Der sequence = Der.read(value, outer.start(), 0x30);
		Der tagged = Der.read(value, sequence.start(), 0xA1);
		Der nonce = Der.read(value, tagged.start(), 0x04);
		return Arrays.copyOfRange(value, nonce.start(), nonce.start() + nonce.length());
	}

	private record Der(int start, int length) {

		static Der read(byte[] der, int at, int expectedTag) {
			require(at + 2 <= der.length && (der[at] & 0xFF) == expectedTag, "nonce");
			int length = der[at + 1] & 0xFF;
			int start = at + 2;
			if (length > 0x7F) {
				int bytes = length & 0x7F;
				require(bytes <= 2 && start + bytes <= der.length, "nonce");
				length = 0;
				for (int i = 0; i < bytes; i++) {
					length = (length << 8) | (der[start + i] & 0xFF);
				}
				start += bytes;
			}
			require(start + length <= der.length, "nonce");
			return new Der(start, length);
		}
	}

	static byte[] uncompressedPoint(ECPublicKey key) {
		int size = (key.getParams().getCurve().getField().getFieldSize() + 7) / 8;
		return concat(new byte[] { 0x04 }, fixed(key.getW().getAffineX(), size), fixed(key.getW().getAffineY(), size));
	}

	private static byte[] fixed(BigInteger value, int size) {
		byte[] raw = value.toByteArray();
		byte[] out = new byte[size];
		int copy = Math.min(raw.length, size);
		System.arraycopy(raw, raw.length - copy, out, size - copy, copy);
		return out;
	}

	private static long counter(byte[] authData) {
		return ByteBuffer.wrap(authData, 33, 4).getInt() & 0xFFFFFFFFL;
	}

	private JsonNode read(byte[] object) {
		try {
			JsonNode node = cbor.readTree(object);
			require(node != null && node.isObject(), "encoding");
			return node;
		}
		catch (RuntimeException ex) {
			if (ex instanceof Rejected rejected) {
				throw rejected;
			}
			throw new Rejected("encoding");
		}
	}

	private static byte[] bytes(JsonNode node) {
		return node.isBinary() ? node.binaryValue() : null;
	}

	private static X509Certificate certificate(byte[] der) {
		require(der != null, "certificate chain");
		try {
			return (X509Certificate) CertificateFactory.getInstance("X.509")
				.generateCertificate(new ByteArrayInputStream(der));
		}
		catch (GeneralSecurityException | RuntimeException ex) {
			throw new Rejected("certificate chain");
		}
	}

	static byte[] sha256(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static byte[] concat(byte[]... parts) {
		int length = 0;
		for (byte[] part : parts) {
			length += part.length;
		}
		ByteBuffer out = ByteBuffer.allocate(length);
		for (byte[] part : parts) {
			out.put(part);
		}
		return out.array();
	}

	private static void require(boolean condition, String rule) {
		if (!condition) {
			throw new Rejected(rule);
		}
	}

	record RegisteredKey(byte[] publicKey, String environment) {
	}

	/** Which verification rule failed; recorded as a metric tag, never shown in detail to the client. */
	static final class Rejected extends RuntimeException {

		Rejected(String rule) {
			super(rule, null, false, false);
		}

		String rule() {
			return getMessage();
		}
	}
}
