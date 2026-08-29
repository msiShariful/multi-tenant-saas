package com.islamshariful.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer organisation — the isolation boundary of the whole platform.
 *
 * <p>Deliberately <strong>not</strong> annotated with {@code @TenantId}: this is the tenant registry
 * itself, so it must be readable before any tenant context exists (login resolves a tenant by slug
 * before it can know which tenant to scope the user lookup to).
 */
@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** URL/login-safe identifier, e.g. {@code acme-corp}. Globally unique. */
    @Column(name = "slug", nullable = false, unique = true, length = 63)
    private String slug;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status;

    private Tenant(UUID id, String slug, String name) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.status = TenantStatus.ACTIVE;
    }

    /**
     * The identifier is generated here rather than by the database, and that is what makes tenant sign-up a
     * single atomic transaction. Hibernate fixes a session's tenant identifier when the session opens, so the
     * admin user's {@code tenant_id} can only be filled in if the tenant's id is already known before the
     * transaction starts. Letting the database assign it would force sign-up into two transactions and leave
     * an orphaned tenant behind whenever the second one failed.
     *
     * <p>Cost of random v4 ids: inserts scatter across the primary-key B-tree instead of appending to it. At
     * tenant/user volumes that is immaterial; a time-ordered UUIDv7 is the upgrade if it ever stops being.
     */
    public static Tenant create(String slug, String name) {
        return new Tenant(UUID.randomUUID(), Objects.requireNonNull(slug), Objects.requireNonNull(name));
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    public void activate() {
        this.status = TenantStatus.ACTIVE;
    }
}
