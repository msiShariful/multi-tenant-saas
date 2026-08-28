package com.islamshariful.authservice.service;

import com.islamshariful.authservice.config.JwtProperties;
import com.islamshariful.authservice.domain.RefreshToken;
import com.islamshariful.authservice.exception.InvalidRefreshTokenException;
import com.islamshariful.authservice.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, verifies and rotates opaque refresh tokens.
 *
 * <p>Refresh tokens are <em>not</em> JWTs. A JWT refresh token cannot be revoked without server state, which
 * defeats the point of having one: the whole reason to pair a short-lived access token with a long-lived
 * refresh token is that the long-lived half can be taken away. Since state is required anyway, the token is
 * just 256 bits of {@code SecureRandom} and the database row is the source of truth.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 256 bits: far beyond guessing, and matches the SHA-256 digest width used for storage. */
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /** Starts a new rotation family. One family per login, i.e. per device. */
    @Transactional
    public IssuedRefreshToken issueNewFamily(UUID tenantId, UUID userId, ClientMetadata metadata) {
        return issue(tenantId, userId, UUID.randomUUID(), metadata);
    }

    /**
     * Consumes {@code currentTokenId} and issues its successor within the same family.
     *
     * <p>Re-reads the row under a write lock and re-checks usability: the caller's check happened in an
     * earlier transaction, and a concurrent refresh may have consumed the token in between.
     */
    @Transactional
    public IssuedRefreshToken rotate(UUID currentTokenId, ClientMetadata metadata) {
        RefreshToken current = refreshTokenRepository
                .findByIdForUpdate(currentTokenId)
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (!current.isUsableAt(now)) {
            throw new InvalidRefreshTokenException();
        }
        IssuedRefreshToken successor = issue(current.getTenantId(), current.getUserId(), current.getFamilyId(), metadata);
        current.rotateTo(successor.id(), now);
        return successor;
    }

    private IssuedRefreshToken issue(UUID tenantId, UUID userId, UUID familyId, ClientMetadata metadata) {
        byte[] raw = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(raw);
        String value = encoder.encodeToString(raw);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenTtl());

        RefreshToken token = RefreshToken.issue(
                tenantId,
                userId,
                sha256Hex(value),
                familyId,
                expiresAt,
                metadata.userAgent(),
                metadata.ipAddress(),
                now);
        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(token.getId(), value, familyId, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByRawToken(String rawToken) {
        return refreshTokenRepository.findByTokenHash(sha256Hex(rawToken));
    }

    /**
     * Revokes an entire rotation chain.
     *
     * <p>Runs in its own transaction because its most important caller is about to throw: when a replayed
     * token is detected, the revocation has to survive the rejection of the request that detected it.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId) {
        int revoked = refreshTokenRepository.revokeFamily(familyId, clock.instant());
        log.debug("Revoked {} refresh token(s) in family {}", revoked, familyId);
        return revoked;
    }

    @Transactional
    public int revokeAllForUser(UUID userId) {
        return refreshTokenRepository.revokeAllForUser(userId, clock.instant());
    }

    /**
     * SHA-256, not bcrypt.
     *
     * <p>Slow hashing exists to make low-entropy secrets expensive to guess. This secret has 256 bits of
     * entropy, so there is nothing to guess; using bcrypt here would add ~100ms to every refresh and buy
     * nothing. The property that matters — a database dump yields no usable tokens — is preserved.
     */
    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", ex);
        }
    }

    /** @param value the plaintext handed to the client; this is the only moment it exists outside the client */
    public record IssuedRefreshToken(UUID id, String value, UUID familyId, Instant expiresAt) {}
}
