package oneday.common;

import org.springframework.http.HttpStatus;

/** A domain error with a stable machine-readable {@code code}, rendered as RFC 9457 problem detail. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public static ApiException notFound(String what) {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	public static ApiException unauthorized(String code, String message) {
		return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
	}

	public static ApiException forbidden(String code, String message) {
		return new ApiException(HttpStatus.FORBIDDEN, code, message);
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	public static ApiException unprocessable(String code, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
	}

	public static ApiException tooManyRequests(String code, String message) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
	}

	public static ApiException unavailable(String code, String message) {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
	}
}
