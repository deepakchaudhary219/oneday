package oneday.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Every error is a ProblemDetail with a stable {@code code} and the {@code requestId} to quote to support. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApi(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
		problem.setProperty("code", ex.code());
		ex.properties().forEach(problem::setProperty);
		return withRequestId(problem);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setProperty("code", "VALIDATION_FAILED");
		problem.setProperty("errors", errors);
		return withRequestId(problem);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ProblemDetail handleUnreadable(Exception ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request");
		problem.setProperty("code", "MALFORMED_REQUEST");
		return withRequestId(problem);
	}

	/** Framework errors that already know their status (e.g. {@code ResponseStatusException}). */
	@ExceptionHandler(ErrorResponseException.class)
	ResponseEntity<ProblemDetail> handleErrorResponse(ErrorResponseException ex) {
		return ResponseEntity.status(ex.getStatusCode()).body(withRequestId(ex.getBody()));
	}

	/**
	 * A bug. Logged here, while the request id is still on the log context, and answered without internals:
	 * the id is what connects the user's report to this log line.
	 */
	@ExceptionHandler(RuntimeException.class)
	ProblemDetail handleUnexpected(RuntimeException ex) {
		log.error("Unhandled error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Something went wrong on our side. If it keeps happening, contact support with this request id.");
		problem.setProperty("code", "INTERNAL_ERROR");
		return withRequestId(problem);
	}

	private static ProblemDetail withRequestId(ProblemDetail problem) {
		String requestId = RequestIdFilter.current();
		if (requestId != null) {
			problem.setProperty("requestId", requestId);
		}
		return problem;
	}
}
