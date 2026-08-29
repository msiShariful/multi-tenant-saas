package com.islamshariful.authservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Field names follow RFC 6749's token response so any OAuth-aware client library can consume it, even though
 * this service is not a full authorization server.
 *
 * @param expiresIn lifetime of the access token in seconds; clients should refresh before it elapses rather
 *                  than waiting for a 401
 */
@Schema(description = "A freshly issued access/refresh token pair")
public record TokenResponse(
        @Schema(description = "Signed JWT; send as 'Authorization: Bearer <token>'") String accessToken,
        @Schema(description = "Opaque token, single-use: refreshing rotates it") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") long expiresIn,
        Instant issuedAt) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, Instant issuedAt) {
        return new TokenResponse(accessToken, refreshToken, BEARER, expiresIn, issuedAt);
    }
}
