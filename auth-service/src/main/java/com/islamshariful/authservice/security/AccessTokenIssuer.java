package com.islamshariful.authservice.security;

import com.islamshariful.authservice.config.JwtProperties;
import com.islamshariful.authservice.domain.RoleName;
import com.islamshariful.authservice.domain.Tenant;
import com.islamshariful.authservice.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints signed access tokens.
 *
 * <p>The claim set is deliberately small and stable, because it is a contract other services depend on. Two
 * choices worth defending:
 *
 * <ul>
 *   <li>{@code tenant_id} is in the token so downstream services can scope their own queries without
 *       calling back here. It is the reason the token must be asymmetrically signed — a service that could
 *       forge one could forge any tenant.
 *   <li>Roles are embedded rather than looked up per request. The cost is staleness bounded by the access
 *       token TTL: a revoked role stays effective for at most that long. That is the standard stateless-JWT
 *       trade, and the reason the TTL is minutes rather than hours.
 * </ul>
 */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(User user, Tenant tenant) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        List<String> roles = user.roleNames().stream().map(RoleName::name).toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(AuthenticatedUser.CLAIM_TENANT_ID, tenant.getId().toString())
                .claim(AuthenticatedUser.CLAIM_TENANT_SLUG, tenant.getSlug())
                .claim(AuthenticatedUser.CLAIM_EMAIL, user.getEmail())
                .claim(AuthenticatedUser.CLAIM_ROLES, roles)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.keyId())
                .build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, issuedAt, expiresAt);
    }

    public record IssuedAccessToken(String value, Instant issuedAt, Instant expiresAt) {

        public long expiresInSeconds() {
            return java.time.Duration.between(issuedAt, expiresAt).toSeconds();
        }
    }
}
