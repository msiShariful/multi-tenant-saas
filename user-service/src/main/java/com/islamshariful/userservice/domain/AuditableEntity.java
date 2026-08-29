package com.islamshariful.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Audit columns and an optimistic-locking version shared by every aggregate root. */
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
     * Boxed deliberately. Identifiers here are assigned rather than generated, so a primitive version
     * would leave Spring Data looking at a non-null id and calling {@code merge} — a SELECT before every
     * insert. A null {@code Long} says "new".
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
