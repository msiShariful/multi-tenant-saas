package com.islamshariful.authservice.service;

import com.islamshariful.authservice.config.LoginPolicyProperties;
import com.islamshariful.authservice.domain.RefreshToken;
import com.islamshariful.authservice.domain.Tenant;
import com.islamshariful.authservice.domain.User;
import com.islamshariful.authservice.dto.request.ChangePasswordRequest;
import com.islamshariful.authservice.dto.request.LoginRequest;
import com.islamshariful.authservice.dto.response.TokenResponse;
import com.islamshariful.authservice.dto.response.UserResponse;
import com.islamshariful.authservice.exception.AccountInactiveException;
import com.islamshariful.authservice.exception.AccountLockedException;
import com.islamshariful.authservice.exception.InvalidCredentialsException;
import com.islamshariful.authservice.exception.InvalidRefreshTokenException;
import com.islamshariful.authservice.exception.ResourceNotFoundException;
import com.islamshariful.authservice.repository.TenantRepository;
import com.islamshariful.authservice.repository.UserRepository;
import com.islamshariful.authservice.security.AccessTokenIssuer;
import com.islamshariful.authservice.security.AuthenticatedUser;
import com.islamshariful.authservice.security.TenantScope;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, token refresh, logout and self-service password change.
 *
 * <p>Deliberately does not use {@code AuthenticationManager}/{@code UserDetailsService}. That contract is
 * {@code loadUserByUsername(String)} — a single-column lookup — while an identity here is keyed by
 * {@code (tenant, email)}. The usual workarounds (encoding the tenant into the username string, or stashing
 * it in a {@code ThreadLocal} for {@code UserDetailsService} to read back) trade a clear service method for
 * hidden coupling. Verifying the password explicitly is less code and reads as what it is.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final LoginPolicyProperties loginPolicy;
    private final TenantScope tenantScope;
    private final Clock clock;

    /**
     * A real hash, verified against whenever the account does not exist.
     *
     * <p>Without it, an unknown email returns in microseconds while a known one costs a bcrypt verification.
     * That difference is measurable over the network and turns login into an account-existence oracle. The
     * fix is to always pay the same price.
     */
    private final String timingEqualisationHash;

    public AuthenticationService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            AccessTokenIssuer accessTokenIssuer,
            PasswordEncoder passwordEncoder,
            LoginPolicyProperties loginPolicy,
            TenantScope tenantScope,
            Clock clock) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenIssuer = accessTokenIssuer;
        this.passwordEncoder = passwordEncoder;
        this.loginPolicy = loginPolicy;
        this.tenantScope = tenantScope;
        this.clock = clock;
        this.timingEqualisationHash = passwordEncoder.encode("timing-equalisation-" + UUID.randomUUID());
    }

    // ------------------------------------------------------------------ login

    public TokenResponse login(LoginRequest request, ClientMetadata metadata) {
        // The tenant registry is not tenant-scoped, so this read needs no context -- and must happen first,
        // because the context it produces has to be in place before the login transaction opens.
        Tenant tenant = tenantRepository
                .findBySlug(normaliseSlug(request.tenantSlug()))
                .orElse(null);
        if (tenant == null) {
            passwordEncoder.matches(request.password(), timingEqualisationHash);
            throw new InvalidCredentialsException();
        }
        if (!tenant.isActive()) {
            throw AccountInactiveException.suspendedTenant();
        }

        LoginOutcome outcome = tenantScope.execute(tenant.getId(), () -> attemptLogin(tenant, request, metadata));
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.tokens();
    }

    /**
     * Returns the failure instead of throwing it.
     *
     * <p>The failed-attempt counter and the lockout timestamp are written in this transaction. Throwing from
     * inside it would roll both back, so the lockout would never engage and the brute-force protection would
     * be decorative. The caller re-throws once the transaction has committed.
     */
    private LoginOutcome attemptLogin(Tenant tenant, LoginRequest request, ClientMetadata metadata) {
        Instant now = clock.instant();
        User user = userRepository.findByEmail(normaliseEmail(request.email())).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), timingEqualisationHash);
            return LoginOutcome.failed(new InvalidCredentialsException());
        }
        if (user.isLockedAt(now)) {
            return LoginOutcome.failed(new AccountLockedException(user.getLockedUntil()));
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean lockedNow =
                    user.registerFailedLogin(loginPolicy.maxFailedAttempts(), loginPolicy.lockoutDuration(), now);
            if (lockedNow) {
                log.warn("Locked user {} in tenant {} after {} failed attempts",
                        user.getId(), tenant.getSlug(), loginPolicy.maxFailedAttempts());
                return LoginOutcome.failed(new AccountLockedException(user.getLockedUntil()));
            }
            return LoginOutcome.failed(new InvalidCredentialsException());
        }
        if (!user.isActive()) {
            return LoginOutcome.failed(AccountInactiveException.disabledUser());
        }

        // Managed entity: JPA dirty checking flushes the login bookkeeping at commit, no explicit save needed.
        user.registerSuccessfulLogin(now);
        log.info("Login succeeded for user {} in tenant {}", user.getId(), tenant.getSlug());
        return LoginOutcome.succeeded(issueTokenPair(user, tenant, metadata));
    }

    // ---------------------------------------------------------------- refresh

    public TokenResponse refresh(String rawRefreshToken, ClientMetadata metadata) {
        RefreshToken token = refreshTokenService
                .findByRawToken(rawRefreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);

        // Presenting an already-consumed token means two parties hold it. Which one is legitimate is
        // unknowable, so the entire family dies and every session on that chain must log in again.
        if (token.isRevoked()) {
            refreshTokenService.revokeFamily(token.getFamilyId());
            log.warn("Refresh token reuse detected for user {} (family {}); revoked the family",
                    token.getUserId(), token.getFamilyId());
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpiredAt(clock.instant())) {
            throw new InvalidRefreshTokenException();
        }

        return tenantScope.execute(token.getTenantId(), () -> completeRefresh(token, metadata));
    }

    private TokenResponse completeRefresh(RefreshToken token, ClientMetadata metadata) {
        Tenant tenant = tenantRepository
                .findById(token.getTenantId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!tenant.isActive()) {
            throw AccountInactiveException.suspendedTenant();
        }
        // Tenant-filtered: an id from another tenant's token could not resolve here even if one were forged.
        User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        if (!user.isActive()) {
            throw AccountInactiveException.disabledUser();
        }

        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(token.getId(), metadata);
        AccessTokenIssuer.IssuedAccessToken access = accessTokenIssuer.issue(user, tenant);
        return TokenResponse.of(access.value(), rotated.value(), access.expiresInSeconds(), access.issuedAt());
    }

    // ----------------------------------------------------------------- logout

    /**
     * Always succeeds, whether or not the token existed.
     *
     * <p>Reporting "unknown token" would confirm to a caller holding a stolen token that it had already been
     * revoked. Ownership is checked so that a token belonging to a different user is ignored rather than
     * revoked on their behalf.
     */
    public void logout(AuthenticatedUser caller, String rawRefreshToken) {
        refreshTokenService
                .findByRawToken(rawRefreshToken)
                .filter(token -> token.getUserId().equals(caller.userId()))
                .ifPresent(token -> refreshTokenService.revokeFamily(token.getFamilyId()));
    }

    // -------------------------------------------------------- self-service me

    @Transactional(readOnly = true)
    public UserResponse currentUser(AuthenticatedUser caller) {
        return userRepository
                .findById(caller.userId())
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.userId()));
    }

    /**
     * Changing a password invalidates every refresh token the user holds.
     *
     * <p>A password change is normally a response to compromise; leaving existing sessions alive would let
     * whoever prompted it stay signed in indefinitely. Access tokens already issued cannot be recalled —
     * that residual window is exactly why {@code access-token-ttl} is measured in minutes.
     */
    @Transactional
    public void changePassword(AuthenticatedUser caller, ChangePasswordRequest request) {
        User user = userRepository
                .findById(caller.userId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.userId()));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password changed for user {}; all refresh tokens revoked", user.getId());
    }

    // ---------------------------------------------------------------- helpers

    private TokenResponse issueTokenPair(User user, Tenant tenant, ClientMetadata metadata) {
        AccessTokenIssuer.IssuedAccessToken access = accessTokenIssuer.issue(user, tenant);
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issueNewFamily(tenant.getId(), user.getId(), metadata);
        return TokenResponse.of(access.value(), refresh.value(), access.expiresInSeconds(), access.issuedAt());
    }

    static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static String normaliseSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }

    /** Carries a login result across the transaction boundary so the failure can be thrown after commit. */
    private record LoginOutcome(TokenResponse tokens, RuntimeException failure) {

        static LoginOutcome succeeded(TokenResponse tokens) {
            return new LoginOutcome(tokens, null);
        }

        static LoginOutcome failed(RuntimeException failure) {
            return new LoginOutcome(null, failure);
        }
    }
}
