package com.islamshariful.authservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * Signing and validation settings for access tokens.
 *
 * @param issuer            the {@code iss} claim; downstream services must be configured with the same value
 * @param audience          the {@code aud} claim, identifying the API these tokens are good for
 * @param accessTokenTtl    kept short (minutes): access tokens are not revocable, so the TTL <em>is</em> the
 *                          revocation window for a disabled user or a suspended tenant
 * @param refreshTokenTtl   long-lived, but revocable because the server keeps a row per token
 * @param keyId             the {@code kid} published in JWKS and stamped into each token header, so keys can
 *                          be rotated without a flag day: publish both, sign with the new one, retire the old
 * @param privateKeyLocation PKCS#8 PEM; when absent a throwaway key pair is generated at boot (development only)
 * @param publicKeyLocation  X.509 PEM matching {@code privateKeyLocation}
 */
@Validated
@ConfigurationProperties(prefix = "tenantbase.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank String keyId,
        Resource privateKeyLocation,
        Resource publicKeyLocation) {

    public boolean hasConfiguredKeyPair() {
        return privateKeyLocation != null && publicKeyLocation != null;
    }
}
