package oneday.platform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import oneday.platform.IdempotencyStore.Stored;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * {@code Idempotency-Key} for POSTs (the IETF httpapi draft / Stripe pattern). Mobile networks drop responses;
 * a client that retries a POST with the same key gets the original response replayed instead of a second
 * signal, report, plan or moment. Keys are scoped per account, bound to the request body (reusing a key for a
 * different request is refused), kept for 24 h, and work across replicas because they live in the database.
 * Server errors are not stored, so a retry after a 5xx really retries.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class IdempotencyFilter extends OncePerRequestFilter {

	static final String HEADER = "Idempotency-Key";

	static final String REPLAYED = "Idempotent-Replayed";

	private final IdempotencyStore store;

	public IdempotencyFilter(IdempotencyStore store) {
		this.store = store;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || request.getHeader(HEADER) == null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String key = request.getHeader(HEADER).trim();
		Optional<String> user = currentUser();
		if (user.isEmpty()) {
			chain.doFilter(request, response); // unauthenticated POSTs (sign-in) are not keyed
			return;
		}
		if (key.isEmpty() || key.length() > 64) {
			problem(response, 400, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1 to 64 characters");
			return;
		}
		byte[] body = request.getInputStream().readAllBytes();
		String hash = sha256(request.getRequestURI() + "\n" + new String(body, StandardCharsets.UTF_8));
		Optional<Stored> existing = store.claim(user.get(), key, hash);
		if (existing.isPresent()) {
			Stored stored = existing.get();
			if (!stored.requestHash().equals(hash)) {
				problem(response, 422, "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was used for a different request");
			}
			else if (stored.status() == null) {
				problem(response, 409, "REQUEST_IN_PROGRESS", "The original request is still being processed");
			}
			else {
				response.setStatus(stored.status());
				response.setHeader(REPLAYED, "true");
				if (stored.contentType() != null) {
					response.setContentType(stored.contentType());
				}
				if (stored.body() != null) {
					response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
				}
			}
			return;
		}
		ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
		boolean completed = false;
		try {
			chain.doFilter(new CachedBodyRequest(request, body), wrapped);
			completed = true;
		}
		finally {
			int status = wrapped.getStatus();
			if (completed && status < 500) {
				store.complete(user.get(), key, status, wrapped.getContentType(),
						new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8));
			}
			else {
				store.release(user.get(), key);
			}
			wrapped.copyBodyToResponse();
		}
	}

	private static Optional<String> currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getPrincipal() instanceof Jwt jwt ? Optional.of(jwt.getSubject()) : Optional.empty();
	}

	private static void problem(HttpServletResponse response, int status, String code, String detail)
			throws IOException {
		response.setStatus(status);
		response.setContentType("application/problem+json");
		response.getOutputStream()
			.write(("{\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}")
				.getBytes(StandardCharsets.UTF_8));
	}

	static String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Lets the body, already read for hashing, be read again by the controller. */
	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream in = new ByteArrayInputStream(body);
			return new ServletInputStream() {

				@Override
				public int read() {
					return in.read();
				}

				@Override
				public boolean isFinished() {
					return in.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
}
