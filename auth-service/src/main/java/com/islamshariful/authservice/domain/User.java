package com.islamshariful.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * A login identity inside one tenant.
 *
 * <p>The {@code tenantId} field carries {@link TenantId}, which is what actually makes this service
 * multi-tenant: Hibernate appends {@code AND tenant_id = ?} to <em>every</em> select, update and delete
 * against this entity and populates the column on insert. A developer who writes
 * {@code userRepository.findByEmail(email)} cannot accidentally read another tenant's row — the
 * restriction is added below the repository, not by convention.
 *
 * <p>Email uniqueness is {@code (tenant_id, email)}, not global: the same person may hold an account in
 * several tenants, which is why login takes a tenant slug alongside the email.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_tenant_email", columnNames = {"tenant_id", "email"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Always stored lower-cased; normalisation happens at the service boundary. */
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    /** A {@code DelegatingPasswordEncoder} hash, prefixed with its algorithm id (e.g. {@code {bcrypt}...}). */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * Eagerly fetched because every single read of a user is followed by a read of its roles (token
     * issuance, the admin listing, {@code /me}); a lazy collection here buys nothing but N+1 queries.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    private User(UUID id, String email, String passwordHash, String firstName, String lastName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
    }

    /**
     * The tenant is intentionally not a parameter: Hibernate fills {@code tenant_id} from the current
     * tenant identifier at flush time, so callers must open the right tenant scope instead of passing an
     * id that could disagree with it.
     */
    public static User create(String email, String passwordHash, String firstName, String lastName) {
        return new User(UUID.randomUUID(), email, passwordHash, firstName, lastName);
    }

    public Set<RoleName> roleNames() {
        return roles.stream().map(Role::getName).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public void replaceRoles(Collection<Role> newRoles) {
        this.roles.clear();
        this.roles.addAll(newRoles);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerSuccessfulLogin(Instant now) {
        this.lastLoginAt = now;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Returns {@code true} when this failure tripped the lockout threshold. */
    public boolean registerFailedLogin(int maxAttempts, java.time.Duration lockoutDuration, Instant now) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = now.plus(lockoutDuration);
            this.failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }
}
