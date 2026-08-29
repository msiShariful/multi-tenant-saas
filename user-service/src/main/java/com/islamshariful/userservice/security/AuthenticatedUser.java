package com.islamshariful.userservice.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * The caller, projected out of the access token auth-service signed.
 *
 * <p>These claims are the entire integration contract between the two services. There is no shared
 * database and no call back to auth-service: everything user-service knows about who is asking arrives
 * inside a document whose signature it has already verified against auth-service's published key.
 *
 * <p>Exposed as the {@code Authentication} principal so controllers declare
 * {@code @AuthenticationPrincipal AuthenticatedUser caller} and services never touch Spring Security types.
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, String tenantSlug, String email, Set<String> roles) {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_TENANT_SLUG = "tenant_slug";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";

    public AuthenticatedUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * A token whose signature verified but whose claims are missing is malformed, not merely
     * unauthorised. {@link InvalidBearerTokenException} produces 401 {@code invalid_token} with the right
     * {@code WWW-Authenticate} header; a {@code NullPointerException} would surface as a 500 and tell an
     * attacker something about internals.
     */
    public static AuthenticatedUser from(Jwt jwt) {
        UUID userId = parseUuid(jwt.getSubject(), "sub");
        UUID tenantId = parseUuid(jwt.getClaimAsString(CLAIM_TENANT_ID), CLAIM_TENANT_ID);
        String tenantSlug = require(jwt.getClaimAsString(CLAIM_TENANT_SLUG), CLAIM_TENANT_SLUG);
        String email = require(jwt.getClaimAsString(CLAIM_EMAIL), CLAIM_EMAIL);
        List<String> roles = jwt.getClaimAsStringList(CLAIM_ROLES);
        return new AuthenticatedUser(
                userId, tenantId, tenantSlug, email, roles == null ? Set.of() : new LinkedHashSet<>(roles));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    private static String require(String value, String claim) {
        if (value == null || value.isBlank()) {
            throw new InvalidBearerTokenException("Access token is missing the required '%s' claim".formatted(claim));
        }
        return value;
    }

    private static UUID parseUuid(String value, String claim) {
        try {
            return UUID.fromString(require(value, claim));
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Claim '%s' is not a valid UUID".formatted(claim));
        }
    }
}
