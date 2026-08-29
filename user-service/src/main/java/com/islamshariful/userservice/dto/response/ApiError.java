package com.islamshariful.userservice.dto.response;

import com.islamshariful.userservice.exception.ValidationError;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * The documentation model for every error this service returns.
 *
 * <p>Nothing constructs it — errors are Spring {@code ProblemDetail} instances. It exists so OpenAPI can
 * describe that shape, which {@code ProblemDetail} cannot: its extension members live in an untyped
 * property map no schema generator can see. {@code ProblemDetailContractTest} keeps the two in step.
 */
@Schema(name = "ApiError",
        description = "RFC 9457 problem document, returned as application/problem+json for every failure.")
public record ApiError(
        @Schema(example = "https://tenantbase.dev/problems/resource-not-found") String type,
        @Schema(example = "Not Found") String title,
        @Schema(example = "404") Integer status,
        @Schema(example = "Profile 'a3f1…' was not found") String detail,
        @Schema(example = "/api/v1/profiles/a3f1") String instance,
        @Schema(description = "Stable, machine-readable. Branch on this.", example = "RESOURCE_NOT_FOUND")
                String code,
        Instant timestamp,
        @Schema(description = "Only on 400 validation failures") List<ValidationError> errors,
        @Schema(description = "Only where a log line is worth correlating") String traceId) {}
