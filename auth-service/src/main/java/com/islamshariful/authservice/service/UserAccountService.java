package com.islamshariful.authservice.service;

import com.islamshariful.authservice.domain.Role;
import com.islamshariful.authservice.domain.RoleName;
import com.islamshariful.authservice.domain.User;
import com.islamshariful.authservice.dto.request.CreateUserRequest;
import com.islamshariful.authservice.dto.response.PageResponse;
import com.islamshariful.authservice.dto.response.UserResponse;
import com.islamshariful.authservice.exception.BusinessRuleException;
import com.islamshariful.authservice.exception.DuplicateResourceException;
import com.islamshariful.authservice.exception.ResourceNotFoundException;
import com.islamshariful.authservice.repository.RoleRepository;
import com.islamshariful.authservice.repository.UserRepository;
import com.islamshariful.authservice.security.AuthenticatedUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator-facing identity management, always within the caller's own tenant.
 *
 * <p>No method takes a tenant id. The request is authenticated, so {@code TenantContextFilter} has already
 * scoped the thread from the token's {@code tenant_id} claim and every query below inherits it. A caller
 * cannot widen that scope, because there is no parameter through which to try.
 *
 * <p>Note the boundary with user-service: credentials, status and role assignment live here because they are
 * authentication concerns; display names, avatars and preferences belong to user-service. The two share only
 * the user id.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(
            UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = AuthenticationService.normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                    "EMAIL_ALREADY_REGISTERED", "A user with that email already exists in this tenant");
        }
        User user = User.create(
                email,
                passwordEncoder.encode(request.password()),
                trimToNull(request.firstName()),
                trimToNull(request.lastName()));
        user.replaceRoles(resolveRoles(request.roles()));
        User saved = userRepository.saveAndFlush(user);
        log.info("Created user {} in tenant {}", saved.getId(), saved.getTenantId());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.from(userRepository.findAllByOrderByCreatedAtDesc(pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return userRepository
                .findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    /**
     * Replaces a user's roles.
     *
     * <p>Refuses to remove the tenant's last administrator. Without that check a tenant can lock itself out
     * permanently, and recovery requires a database edit — a support ticket that should never be possible to
     * create through the API.
     */
    @Transactional
    public UserResponse replaceRoles(UUID userId, Set<RoleName> requestedRoles) {
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        boolean isAdministrator = user.roleNames().contains(RoleName.TENANT_ADMIN);
        boolean staysAdministrator = requestedRoles.contains(RoleName.TENANT_ADMIN);
        if (isAdministrator && !staysAdministrator && userRepository.countActiveWithRole(RoleName.TENANT_ADMIN) <= 1) {
            throw new BusinessRuleException(
                    "LAST_ADMINISTRATOR", "A tenant must keep at least one administrator");
        }

        user.replaceRoles(resolveRoles(requestedRoles));
        log.info("Roles for user {} set to {}", userId, requestedRoles);
        return UserResponse.from(user);
    }

    private Set<Role> resolveRoles(Set<RoleName> names) {
        List<Role> roles = roleRepository.findByNameIn(names);
        if (roles.size() != names.size()) {
            throw new ResourceNotFoundException("One or more of the requested roles do not exist");
        }
        return Set.copyOf(roles);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
