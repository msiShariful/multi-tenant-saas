package com.islamshariful.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * A user's profile, owned entirely by this service.
 *
 * <p><b>The primary key is the auth-service user id</b> — the {@code sub} claim of the access token, not a
 * surrogate of our own. That makes the one-profile-per-user rule structural rather than a constraint
 * somebody has to remember: there is nowhere to put a second row. It also means no lookup table and no
 * join to resolve "the profile for this caller".
 *
 * <p>There is deliberately no foreign key to the users table, because that table is in another service's
 * database. The token is the integrity guarantee instead: a signed assertion from auth-service that this
 * user exists. That is weaker than a foreign key — a user deleted in auth-service leaves an orphan here
 * until an event arrives to clean it up — and it is the price of services owning their own data.
 *
 * <p>{@code email} is a <em>projection</em> of auth-service's copy, refreshed from the token on every
 * request that touches the profile. It exists so the directory listing has something to show and search;
 * it is never the source of truth, and nothing here may treat it as authoritative.
 */
@Entity
@Table(name = "user_profiles", indexes = @Index(name = "idx_user_profiles_tenant_id", columnList = "tenant_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends AuditableEntity {

    /** The auth-service user id. Assigned, never generated. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Projection of auth-service's email, kept fresh from the token. Not authoritative. */
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "locale", length = 35)
    private String locale;

    private UserProfile(UUID id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }

    /**
     * Materialises a profile from the claims of a validated token.
     *
     * <p>The tenant is not a parameter: Hibernate fills {@code tenant_id} from the session's tenant at
     * flush time, so callers must be scoped correctly rather than passing an id that could disagree.
     */
    public static UserProfile fromToken(UUID userId, String email, String displayName) {
        return new UserProfile(userId, email, displayName);
    }

    public void updateProfile(String displayName, String bio, String avatarUrl, String phoneNumber,
            String timeZone, String locale) {
        this.displayName = displayName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.phoneNumber = phoneNumber;
        this.timeZone = timeZone;
        this.locale = locale;
    }

    /** Keeps the email projection in step with the token, which is the only place it can come from. */
    public void refreshEmail(String emailFromToken) {
        if (emailFromToken != null && !emailFromToken.equals(this.email)) {
            this.email = emailFromToken;
        }
    }
}
