package oneday.integrations;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local stand-in for Google's token endpoint and vendor APIs. The token endpoint verifies the RS256 JWT
 * assertion against the service account's public key, so the OAuth flow is tested for real.
 */
public final class FakeVendorServer implements AutoCloseable {

	public record Call(String path, Map<String, String> headers, String body) {
	}

	public final List<Call> calls = new CopyOnWriteArrayList<>();

	public final AtomicInteger tokenRequests = new AtomicInteger();

	private final Map<String, Function<Call, Response>> routes = new ConcurrentHashMap<>();

	private final HttpServer server;

	private final KeyPair keys;

	public record Response(int status, String body) {
	}

	public FakeVendorServer() throws IOException, NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		this.keys = generator.generateKeyPair();
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
		route("/token", call -> {
			tokenRequests.incrementAndGet();
			return validAssertion(call.body()) ? new Response(200, "{\"access_token\":\"tok-" + tokenRequests.get()
					+ "\",\"expires_in\":3600}") : new Response(400, "{\"error\":\"invalid_grant\"}");
		});
	}

	public String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public void route(String path, Function<Call, Response> handler) {
		routes.put(path, handler);
	}

	/** A service-account JSON key whose token endpoint is this server. */
	public Path credentials(Path dir) throws IOException {
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, new byte[] { 10 }).encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
		Path file = dir.resolve("service-account.json");
		Files.writeString(file, """
				{"type":"service_account","project_id":"oneday-test","client_email":"push@oneday-test.iam.gserviceaccount.com",
				 "private_key":"%s","token_uri":"%s/token"}
				""".formatted(pem.replace("\n", "\\n"), baseUrl()));
		return file;
	}

	private boolean validAssertion(String form) {
		try {
			String assertion = java.net.URLDecoder.decode(form.substring(form.indexOf("assertion=") + 10),
					StandardCharsets.UTF_8);
			String[] parts = assertion.split("\\.");
			Signature verifier = Signature.getInstance("SHA256withRSA");
			verifier.initVerify(keys.getPublic());
			verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
			return form.contains("jwt-bearer") && verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
		}
		catch (Exception ex) {
			return false;
		}
	}

	private void handle(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		Map<String, String> headers = new ConcurrentHashMap<>();
		exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
		Call call = new Call(exchange.getRequestURI().getPath(), headers, body);
		calls.add(call);
		Function<Call, Response> handler = routes.get(call.path());
		Response response = handler == null ? new Response(404, "{}") : handler.apply(call);
		byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(response.status(), bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
