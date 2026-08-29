package com.islamshariful.userservice.exception;

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
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single source of truth for what an error looks like on the wire — deliberately identical to
 * auth-service's, so a client written against one service handles the other's failures unchanged.
 *
 * <p>Authentication and authorisation failures are handled here too. They are raised inside the security
 * filter chain, before any {@code @ControllerAdvice} is reachable, which is how an API ends up with two
 * different error shapes; {@code ProblemDetailAuthenticationHandlers} routes them back into this class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://tenantbase.dev/problems/";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        log.debug("Handled API exception [{}]: {}", ex.getCode(), ex.getMessage());
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return problem(
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required to access this resource");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String traceId = traceId();
        log.warn("Data integrity violation [traceId={}]", traceId, ex);
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "CONSTRAINT_VIOLATION",
                "The request conflicts with the current state of the resource");
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

    /** The caller gets a trace id and nothing else; stack traces and SQL stay in the log. */
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
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** Applies the same extensions to the responses this base class produces on its own. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setProperty("code", HttpStatus.valueOf(status.value()).name());
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

    private String traceId() {
        String existing = MDC.get("traceId");
        return existing != null ? existing : UUID.randomUUID().toString();
    }
}
