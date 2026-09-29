package oneday.common;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id: the app's own {@code X-Request-Id} if it sends a well-formed one, else a new
 * one. It is on every log line of the request and on every response, including errors, so a user's
 * "something went wrong" can be traced to the exact server logs. Runs before security, so 401s carry it too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	public static final String MDC_KEY = "requestId";

	/** Anything else (log-injection attempts, huge values) is replaced rather than echoed into logs. */
	private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9._-]{8,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String supplied = request.getHeader(HEADER);
		String requestId = supplied != null && WELL_FORMED.matcher(supplied).matches() ? supplied : Ids.newId();
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	/** The current request's id, for error bodies. */
	public static String current() {
		return MDC.get(MDC_KEY);
	}
}
