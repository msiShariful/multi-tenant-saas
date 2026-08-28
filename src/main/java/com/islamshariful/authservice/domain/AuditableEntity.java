package com.islamshariful.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Audit columns and an optimistic-locking version shared by every aggregate root.
 *
 * <p>{@code @Version} is not decoration: two concurrent role updates on the same user would otherwise
 * last-write-wins silently. With it, the loser gets an {@code OptimisticLockingFailureException}
 * which the global handler surfaces as HTTP 409.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Boxed on purpose. Spring Data decides whether {@code save()} means {@code persist} or {@code merge};
     * with application-assigned ids a primitive version would leave it looking at a non-null id and calling
     * {@code merge}, costing a pointless SELECT before every insert. A null {@code Long} says "new".
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
