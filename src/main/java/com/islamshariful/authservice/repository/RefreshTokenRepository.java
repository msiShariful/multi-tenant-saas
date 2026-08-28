package com.islamshariful.authservice.repository;

import com.islamshariful.authservice.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Row-level lock taken before rotation.
     *
     * <p>Without it two concurrent refreshes with the same token both read {@code revoked_at IS NULL}, both
     * rotate, and the family silently forks — the losing branch then looks like a replay and revokes the
     * user's session. {@code SELECT ... FOR UPDATE} serialises them: the second waits, re-reads the revoked
     * row, and is correctly rejected.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.id = :id")
    Optional<RefreshToken> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Revokes an entire rotation chain. Invoked on logout and, critically, when an already-rotated token is
     * replayed: at that point either the client or an attacker holds a copy, and the only safe assumption is
     * that the chain is compromised.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /** Used when a password changes: every existing session must die with the old credential. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Housekeeping for rows that can no longer authenticate anything. Expired tokens are deleted outright;
     * revoked-but-unexpired ones are kept until expiry so replay of a leaked token is still detectable.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
