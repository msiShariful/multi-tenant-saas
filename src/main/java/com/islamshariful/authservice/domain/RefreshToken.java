package com.islamshariful.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The server side of an opaque refresh token.
 *
 * <p>Three design points a reviewer should look for:
 *
 * <ol>
 *   <li><b>Only the hash is stored.</b> A database leak yields no usable tokens. SHA-256 rather than
 *       bcrypt is correct here: the secret is 256 bits of {@code SecureRandom}, so it is not brute
 *       forceable and a deliberately slow hash would only add latency to every refresh.
 *   <li><b>No {@code @TenantId}.</b> Unlike {@link User}, this row is looked up by its secret on a public
 *       endpoint, <em>before</em> any tenant context exists; a Hibernate tenant filter would make the
 *       lookup return nothing. The tenant is a plain column, read <em>from</em> the row to establish
 *       context. Safety does not regress: the lookup key is unguessable.
 *   <li><b>{@code familyId} enables reuse detection.</b> Every rotation keeps the family; presenting an
 *       already-rotated token means the chain leaked, so the whole family is revoked (OAuth 2.0 Security
 *       BCP, RFC 9700 §4.14.2).
 * </ol>
 *
 * <p>{@code userId} is a plain UUID rather than a {@code @ManyToOne User} on purpose: users and refresh
 * tokens are separate aggregates, and a lazy association would try to load a tenant-filtered {@code User}
 * at a moment when the tenant context is not yet established. The foreign key still exists in the schema.
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
            @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
            @Index(name = "idx_refresh_tokens_family_id", columnList = "family_id"),
            @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Lower-case hex SHA-256 of the token that was handed to the client. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    /** Shared by every token in one rotation chain; the unit of revocation on reuse. */
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private RefreshToken(
            UUID id,
            UUID tenantId,
            UUID userId,
            String tokenHash,
            UUID familyId,
            Instant expiresAt,
            String userAgent,
            String ipAddress,
            Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.createdAt = now;
    }

    public static RefreshToken issue(
            UUID tenantId,
            UUID userId,
            String tokenHash,
            UUID familyId,
            Instant expiresAt,
            String userAgent,
            String ipAddress,
            Instant now) {
        return new RefreshToken(
                UUID.randomUUID(), tenantId, userId, tokenHash, familyId, expiresAt, userAgent, ipAddress, now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsableAt(Instant now) {
        return !isRevoked() && !isExpiredAt(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public void rotateTo(UUID successorId, Instant now) {
        revoke(now);
        this.replacedById = successorId;
    }
}
