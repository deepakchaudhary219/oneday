package oneday.attestation;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * Plays Apple's part for tests: a stand-in App Attestation root and intermediate CA, and Secure Enclave-like
 * device keys that produce attestation and assertion objects in Apple's format.
 */
public final class FakeAppleAttestation {

	private static final CBORMapper CBOR = CBORMapper.builder().build();

	private final Instant now;

	private final KeyPair rootKeys = ecKeys();

	private final X509Certificate root;

	private final KeyPair intermediateKeys = ecKeys();

	private final X509Certificate intermediate;

	public FakeAppleAttestation(Instant now) {
		this.now = now;
		this.root = certificate("CN=Stand-in App Attestation Root CA", rootKeys, "CN=Stand-in App Attestation Root CA",
				rootKeys, true, null);
		this.intermediate = certificate("CN=Stand-in App Attestation CA 1", intermediateKeys,
				"CN=Stand-in App Attestation Root CA", rootKeys, true, null);
	}

	public X509Certificate root() {
		return root;
	}

	/** Writes the stand-in root as PEM, for {@code oneday.attestation.apple.root-ca}. */
	public Path writeRoot(Path dir) {
		try {
			Path file = dir.resolve("root.pem");
			Files.writeString(file, "-----BEGIN CERTIFICATE-----\n"
					+ Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(root.getEncoded())
					+ "\n-----END CERTIFICATE-----\n");
			return file;
		}
		catch (IOException | GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public Device device(String appId) {
		return new Device(appId, ecKeys());
	}

	/** An app install: one Secure Enclave key and its counter. */
	public final class Device {

		private final byte[] rpIdHash;

		private final KeyPair keys;

		private final byte[] keyId;

		private final AtomicLong counter = new AtomicLong();

		private Device(String appId, KeyPair keys) {
			this.rpIdHash = AppAttestVerifier.sha256(appId.getBytes(StandardCharsets.UTF_8));
			this.keys = keys;
			this.keyId = AppAttestVerifier.sha256(AppAttestVerifier.uncompressedPoint((ECPublicKey) keys.getPublic()));
		}

		public String keyId() {
			return Base64.getEncoder().encodeToString(keyId);
		}

		public byte[] keyIdBytes() {
			return keyId.clone();
		}

		/** {@code DCAppAttestService.attestKey} over {@code clientDataHash}. */
		public byte[] attest(byte[] clientDataHash, byte[] aaguid) {
			byte[] cose = CBOR.writeValueAsBytes(Map.of("1", 2));
			byte[] authData = AppAttestVerifier.concat(rpIdHash, new byte[] { 0x40 }, new byte[4], aaguid,
					new byte[] { 0, 32 }, keyId, cose);
			byte[] nonce = AppAttestVerifier.sha256(AppAttestVerifier.concat(authData, clientDataHash));
			X509Certificate leaf = certificate("CN=" + HexFormat.of().formatHex(keyId, 0, 8), keys,
					"CN=Stand-in App Attestation CA 1", intermediateKeys, false, nonce);
			try {
				return CBOR.writeValueAsBytes(Map.of("fmt", "apple-appattest", "attStmt",
						Map.of("x5c", List.of(leaf.getEncoded(), intermediate.getEncoded()), "receipt", new byte[16]),
						"authData", authData));
			}
			catch (GeneralSecurityException ex) {
				throw new IllegalStateException(ex);
			}
		}

		public byte[] attest(byte[] clientDataHash) {
			return attest(clientDataHash, AppAttestVerifier.AAGUID_DEVELOPMENT);
		}

		/** {@code DCAppAttestService.generateAssertion}: the next counter value. */
		public byte[] assert_(byte[] clientDataHash) {
			return assertWithCounter(clientDataHash, counter.incrementAndGet());
		}

		public byte[] assertWithCounter(byte[] clientDataHash, long count) {
			byte[] authenticatorData = AppAttestVerifier.concat(rpIdHash, new byte[] { 0 },
					ByteBuffer.allocate(4).putInt((int) count).array());
			try {
				Signature ecdsa = Signature.getInstance("SHA256withECDSA");
				ecdsa.initSign(keys.getPrivate());
				ecdsa.update(AppAttestVerifier.sha256(AppAttestVerifier.concat(authenticatorData, clientDataHash)));
				return CBOR.writeValueAsBytes(
						Map.of("signature", ecdsa.sign(), "authenticatorData", authenticatorData));
			}
			catch (GeneralSecurityException ex) {
				throw new IllegalStateException(ex);
			}
		}

		/** The {@code X-Device-Integrity} value for a protected request. */
		public String header(String challenge, String action) {
			byte[] hash = AppAttestVerifier.sha256((challenge + "|" + action).getBytes(StandardCharsets.UTF_8));
			return AppAttestAttestor.PREFIX + keyId() + "." + challenge + "."
					+ Base64.getUrlEncoder().withoutPadding().encodeToString(assert_(hash));
		}
	}

	private X509Certificate certificate(String subject, KeyPair subjectKeys, String issuer, KeyPair issuerKeys,
			boolean ca, byte[] nonce) {
		try {
			X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(new X500Name(issuer),
					BigInteger.valueOf(System.nanoTime()), Date.from(now.minus(Duration.ofDays(1))),
					Date.from(now.plus(Duration.ofDays(365))), new X500Name(subject), subjectKeys.getPublic());
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
			if (ca) {
				builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
			}
			if (nonce != null) {
				builder.addExtension(new ASN1ObjectIdentifier(AppAttestVerifier.NONCE_OID), false,
						new DERSequence(new DERTaggedObject(true, 1, new DEROctetString(nonce))));
			}
			return new JcaX509CertificateConverter()
				.getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKeys.getPrivate())));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static KeyPair ecKeys() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
			generator.initialize(new ECGenParameterSpec("secp256r1"));
			return generator.generateKeyPair();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
