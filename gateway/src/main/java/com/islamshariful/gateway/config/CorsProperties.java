package com.islamshariful.gateway.config;

import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser access policy for the edge.
 *
 * @param allowedOrigins exact origins, never {@code *}. A wildcard is incompatible with
 *                       {@code allowCredentials} and would let any site on the internet drive an
 *                       authenticated session from a victim's browser.
 */
@ConfigurationProperties(prefix = "tenantbase.gateway.cors")
public record CorsProperties(
        @NotEmpty List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        Duration maxAge) {

    public CorsProperties {
        allowedMethods = allowedMethods == null || allowedMethods.isEmpty()
                ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : allowedMethods;
        allowedHeaders = allowedHeaders == null || allowedHeaders.isEmpty() ? List.of("*") : allowedHeaders;
        maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
    }
}
