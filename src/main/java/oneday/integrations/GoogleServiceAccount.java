package oneday.integrations;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google OAuth 2.0 for server-to-server calls (FCM, Play Integrity) with a service-account key: a signed
 * RS256 JWT is exchanged for an access token (the JWT-bearer grant), which is cached per scope until shortly
 * before it expires. No Google client library: one small, auditable class.
 */
public class GoogleServiceAccount {

	static final Duration REFRESH_EARLY = Duration.ofMinutes(5);

	private final String clientEmail;

	private final String projectId;

	private final URI tokenUri;

	private final PrivateKey key;

	private final HttpClient http;

	private final JsonMapper json;

	private final Clock clock;

	private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

	public GoogleServiceAccount(Path credentialsFile, HttpClient http, JsonMapper json, Clock clock) {
		this.http = http;
		this.json = json;
		this.clock = clock;
		try {
			JsonNode credentials = json.readTree(Files.readString(credentialsFile));
			this.clientEmail = credentials.get("client_email").asText();
			this.projectId = credentials.get("project_id").asText();
			this.tokenUri = URI.create(credentials.has("token_uri") ? credentials.get("token_uri").asText()
					: "https://oauth2.googleapis.com/token");
			this.key = parsePkcs8(credentials.get("private_key").asText());
		}
		catch (IOException | GeneralSecurityException | RuntimeException ex) {
			throw new IllegalStateException("Unreadable Google service-account credentials " + credentialsFile, ex);
		}
	}

	public String projectId() {
		return projectId;
	}

	/** A valid access token for {@code scope}, fetched or refreshed as needed. */
	public String accessToken(String scope) {
		Instant now = clock.instant();
		CachedToken cached = tokens.get(scope);
		if (cached != null && now.isBefore(cached.expiresAt().minus(REFRESH_EARLY))) {
			return cached.token();
		}
		String assertion = signedJwt(scope, now);
		String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
				+ "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
		try {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(tokenUri)
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("Google token endpoint answered " + response.statusCode());
			}
			JsonNode body = json.readTree(response.body());
			CachedToken fresh = new CachedToken(body.get("access_token").asText(),
					now.plusSeconds(body.has("expires_in") ? body.get("expires_in").asLong() : 3600));
			tokens.put(scope, fresh);
			return fresh.token();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Google token endpoint unreachable", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted", ex);
		}
	}

	private String signedJwt(String scope, Instant now) {
		Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
		String header = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String claims = b64.encodeToString(json.writeValueAsBytes(Map.of("iss", clientEmail, "scope", scope, "aud",
				tokenUri.toString(), "iat", now.getEpochSecond(), "exp", now.plusSeconds(3600).getEpochSecond())));
		try {
			Signature signer = Signature.getInstance("SHA256withRSA");
			signer.initSign(key);
			signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
			return header + "." + claims + "." + b64.encodeToString(signer.sign());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static PrivateKey parsePkcs8(String pem) throws GeneralSecurityException {
		String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s", "");
		return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
	}

	private record CachedToken(String token, Instant expiresAt) {
	}
}
