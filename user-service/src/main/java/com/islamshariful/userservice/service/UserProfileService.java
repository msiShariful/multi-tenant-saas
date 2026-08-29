package com.islamshariful.userservice.service;

import com.islamshariful.userservice.domain.UserProfile;
import com.islamshariful.userservice.dto.request.UpdateProfileRequest;
import com.islamshariful.userservice.dto.response.PageResponse;
import com.islamshariful.userservice.dto.response.ProfileResponse;
import com.islamshariful.userservice.exception.ResourceNotFoundException;
import com.islamshariful.userservice.repository.UserProfileRepository;
import com.islamshariful.userservice.security.AuthenticatedUser;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Profile reads and writes, always within the caller's own tenant.
 *
 * <p>No method takes a tenant id. The request is authenticated, so {@code TenantContextFilter} has already
 * scoped the thread from the token's {@code tenant_id} claim and every query below inherits it. A caller
 * cannot widen that scope because there is no parameter through which to try.
 */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository repository;

    /**
     * Used only by the just-in-time path. The provisioning attempt has to be able to fail and be recovered
     * from, which is impossible inside a transaction that the failure has already marked rollback-only.
     */
    private final TransactionTemplate transactionTemplate;

    public UserProfileService(UserProfileRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Returns the caller's profile, creating it on first access.
     *
     * <p><b>Why just-in-time.</b> Users are created in auth-service, which has no way to reach into this
     * service's database and no business knowing its schema. The alternatives are a synchronous call from
     * auth-service on registration — which couples the two services and makes sign-up fail when this one
     * is down — or an event on a broker, which is the right long-term answer but does not exist yet.
     *
     * <p>The token makes the third option safe: it is a signed assertion from auth-service that this user
     * exists, in this tenant, with this email. Verifying it is exactly as strong as asking auth-service
     * directly, minus the network call and the coupling. When the broker arrives, a {@code UserCreated}
     * consumer simply provisions the row earlier and this path becomes the fallback it already is.
     */
    public ProfileResponse getOrCreateOwnProfile(AuthenticatedUser caller) {
        try {
            return transactionTemplate.execute(status -> ProfileResponse.from(loadOrProvision(caller)));
        } catch (DataIntegrityViolationException race) {
            // A concurrent first request won. Both saw no row, both inserted, and the primary key -- which
            // is the user id, so genuinely unique -- rejected the loser. The row now exists either way.
            log.debug("Lost the provisioning race for user {}; reading the winner's row", caller.userId());
            return transactionTemplate.execute(status -> repository
                    .findById(caller.userId())
                    .map(ProfileResponse::from)
                    .orElseThrow(() -> ResourceNotFoundException.of("Profile", caller.userId())));
        }
    }

    @Transactional
    public ProfileResponse updateOwnProfile(AuthenticatedUser caller, UpdateProfileRequest request) {
        UserProfile profile = loadOrProvision(caller);
        profile.updateProfile(
                trimToNull(request.displayName()),
                trimToNull(request.bio()),
                trimToNull(request.avatarUrl()),
                trimToNull(request.phoneNumber()),
                trimToNull(request.timeZone()),
                trimToNull(request.locale()));
        log.info("Updated profile for user {}", caller.userId());
        return ProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        return repository
                .findById(userId)
                .map(ProfileResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Profile", userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> list(String search, Pageable pageable) {
        var page = (search == null || search.isBlank())
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.search(search.trim(), pageable);
        return PageResponse.from(page, ProfileResponse::from);
    }

    /**
     * Removes a profile.
     *
     * <p>Deletes profile data only — the account itself lives in auth-service and is untouched, so the
     * user can still sign in and would be provisioned a blank profile on next access. Erasing an identity
     * entirely is a two-service operation and belongs behind an event, not here.
     */
    @Transactional
    public void delete(UUID userId) {
        UserProfile profile =
                repository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("Profile", userId));
        repository.delete(profile);
        log.info("Deleted profile for user {}", userId);
    }

    /**
     * Reads the caller's profile or materialises it from the token.
     *
     * <p>Also refreshes the email projection, since the token is the only place a current value can come
     * from and a profile read is the one moment we are guaranteed to have a fresh one.
     */
    private UserProfile loadOrProvision(AuthenticatedUser caller) {
        UserProfile existing = repository.findById(caller.userId()).orElse(null);
        if (existing != null) {
            existing.refreshEmail(caller.email());
            return existing;
        }
        // The tenant is not passed in: Hibernate fills tenant_id from the session's tenant, which the
        // filter established from the validated token.
        UserProfile created = UserProfile.fromToken(caller.userId(), caller.email(), defaultDisplayName(caller));
        UserProfile saved = repository.saveAndFlush(created);
        log.info("Provisioned profile for user {} in tenant {}", saved.getId(), saved.getTenantId());
        return saved;
    }

    /** Something readable in the directory before the user has set anything: the local part of the email. */
    private static String defaultDisplayName(AuthenticatedUser caller) {
        String email = caller.email();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
