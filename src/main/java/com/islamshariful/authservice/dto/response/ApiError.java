package com.islamshariful.authservice.dto.response;

import com.islamshariful.authservice.exception.ValidationError;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * The documentation model for every error this service returns.
 *
 * <p>Nothing constructs this record — errors are produced by {@code GlobalExceptionHandler} as Spring's
 * {@link org.springframework.http.ProblemDetail}. It exists so the OpenAPI document can describe that shape,
 * which {@code ProblemDetail} itself cannot: it carries the RFC 9457 members as fields but the extensions
 * ({@code code}, {@code timestamp}, and the rest) in an untyped property map that no schema generator can see.
 *
 * <p>A hand-written mirror of another class's output invites drift, so
 * {@code ProblemDetailContractTest} asserts that real error responses contain no member this record does not
 * declare. If the handler grows a field, that test fails until this record catches up.
 *
 * @param type      URI identifying the problem kind; stable, and the thing to link documentation from
 * @param code      the member clients should branch on — {@code detail} is prose and may be reworded
 * @param errors    field-level rejections, present only on validation failures
 * @param traceId   present on failures worth correlating with a log line (500, constraint violations)
 * @param lockedUntil present on 423, when the lockout expires
 */
@Schema(name = "ApiError",
        description = "RFC 9457 problem document, returned as application/problem+json for every failure.")
public record ApiError(
        @Schema(example = "https://tenantbase.dev/problems/invalid-credentials") String type,
        @Schema(example = "Unauthorized") String title,
        @Schema(example = "401") Integer status,
        @Schema(example = "Invalid tenant, email or password") String detail,
        @Schema(example = "/api/v1/auth/login") String instance,
        @Schema(description = "Stable, machine-readable. Branch on this.", example = "INVALID_CREDENTIALS")
                String code,
        Instant timestamp,
        @Schema(description = "Only on 400 validation failures") List<ValidationError> errors,
        @Schema(description = "Only where a log line is worth correlating", example = "b7c1f0a2-...")
                String traceId,
        @Schema(description = "Only on 423 Locked") Instant lockedUntil) {}
