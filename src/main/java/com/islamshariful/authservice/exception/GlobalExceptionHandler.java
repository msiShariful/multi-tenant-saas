package com.islamshariful.authservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single source of truth for what an error looks like on the wire.
 *
 * <p>Responses follow RFC 9457 ({@code application/problem+json}) using Spring's built-in
 * {@link ProblemDetail} rather than a hand-rolled envelope, plus three extension members:
 *
 * <ul>
 *   <li>{@code code} — stable, machine-readable; clients branch on this, not on {@code detail}
 *   <li>{@code traceId} — echoed to the caller and written to the log line, so a bug report maps to a log entry
 *   <li>{@code errors} — field-level rejections for validation failures
 * </ul>
 *
 * <p>Note that authentication and authorisation failures are handled here too. They are normally raised
 * inside the security filter chain, before any {@code @ControllerAdvice} is reachable, which is how APIs end
 * up with two different error shapes; {@code ProblemDetailAuthenticationHandlers} routes them back into this
 * class so there is only one.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://tenantbase.dev/problems/";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("Handled API exception [{}]: {}", ex.getCode(), ex.getMessage());
        ProblemDetail problem = problem(ex.getStatus(), ex.getCode(), ex.getMessage());
        if (ex instanceof AccountLockedException locked && locked.getLockedUntil() != null) {
            problem.setProperty("lockedUntil", locked.getLockedUntil());
        }
        return problem;
    }

    /** Raised by the security filter chain for a missing, malformed or expired bearer token. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return problem(
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required to access this resource");
    }

    /** An authenticated caller lacking the required role. The detail stays vague on purpose. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action");
    }

    /**
     * A unique constraint that slipped past the service-level check because another transaction committed
     * first. Reported as 409, never as 500 — it is a legitimate concurrent-write outcome, and the client
     * retrying with different input is the correct fix.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String traceId = traceId();
        log.warn("Data integrity violation [traceId={}]", traceId, ex);
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION", "The request conflicts with the current state of the resource");
        problem.setProperty("traceId", traceId);
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex) {
        log.debug("Optimistic lock failure: {}", ex.getMessage());
        return problem(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "The resource was modified concurrently; re-read it and retry");
    }

    /**
     * The catch-all. The caller gets a trace id and nothing else; stack traces, SQL and class names stay in
     * the log where they belong.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = traceId();
        log.error("Unhandled exception on {} {} [traceId={}]", request.getMethod(), request.getRequestURI(), traceId, ex);
        ProblemDetail problem =
                problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
        problem.setProperty("traceId", traceId);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more request parameters are invalid");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Applies the {@code code} and {@code timestamp} extensions to the responses produced by
     * {@link ResponseEntityExceptionHandler} itself (unreadable JSON, wrong method, unknown route), so those
     * look identical to the ones built here.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setProperty("code", defaultCodeFor(status));
            problem.setProperty("timestamp", Instant.now());
        }
        return response;
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(PROBLEM_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private String defaultCodeFor(HttpStatusCode status) {
        return HttpStatus.valueOf(status.value()).name();
    }

    /** Prefers a tracing id already on the thread so the value matches whatever the log aggregator indexes. */
    private String traceId() {
        String existing = MDC.get("traceId");
        return existing != null ? existing : UUID.randomUUID().toString();
    }
}
