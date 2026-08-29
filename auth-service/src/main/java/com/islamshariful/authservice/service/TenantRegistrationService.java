package com.islamshariful.authservice.service;

import com.islamshariful.authservice.domain.Role;
import com.islamshariful.authservice.domain.RoleName;
import com.islamshariful.authservice.domain.Tenant;
import com.islamshariful.authservice.domain.User;
import com.islamshariful.authservice.dto.request.TenantRegistrationRequest;
import com.islamshariful.authservice.dto.response.TenantRegistrationResponse;
import com.islamshariful.authservice.dto.response.TenantResponse;
import com.islamshariful.authservice.dto.response.UserResponse;
import com.islamshariful.authservice.exception.DuplicateResourceException;
import com.islamshariful.authservice.repository.RoleRepository;
import com.islamshariful.authservice.repository.TenantRepository;
import com.islamshariful.authservice.repository.UserRepository;
import com.islamshariful.authservice.security.TenantScope;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Self-service tenant sign-up: creates the tenant and its first administrator atomically.
 *
 * <p>Atomicity is the whole difficulty. The admin's {@code tenant_id} is written by Hibernate from the
 * session's tenant identifier, which is fixed when the session opens — so the tenant's id must exist
 * <em>before</em> the transaction starts. {@link Tenant#create} assigns it in the application for exactly
 * this reason, which lets both inserts share one transaction and one rollback.
 */
@Service
public class TenantRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistrationService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantScope tenantScope;

    public TenantRegistrationService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            TenantScope tenantScope) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantScope = tenantScope;
    }

    public TenantRegistrationResponse register(TenantRegistrationRequest request) {
        String slug = AuthenticationService.normaliseSlug(request.tenantSlug());
        // A friendly 409 for the overwhelmingly common case. It is not the guarantee -- the unique index is,
        // and a race that beats this check still lands on it and is translated to the same status.
        if (tenantRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException(
                    "TENANT_SLUG_TAKEN", "The tenant identifier '%s' is already in use".formatted(slug));
        }

        Tenant tenant = Tenant.create(slug, request.tenantName().trim());
        return tenantScope.execute(tenant.getId(), () -> provision(tenant, request));
    }

    private TenantRegistrationResponse provision(Tenant tenant, TenantRegistrationRequest request) {
        // Flushed before the user insert: the users.tenant_id foreign key needs the row to be there, and
        // Hibernate has no mapped association telling it to order the two statements itself.
        Tenant savedTenant = tenantRepository.saveAndFlush(tenant);

        Role adminRole = roleRepository
                .findByName(RoleName.TENANT_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "Role catalogue is missing TENANT_ADMIN; check the V2 migration"));

        User administrator = User.create(
                AuthenticationService.normaliseEmail(request.adminEmail()),
                passwordEncoder.encode(request.adminPassword()),
                trimToNull(request.firstName()),
                trimToNull(request.lastName()));
        administrator.replaceRoles(Set.of(adminRole));
        // Flushed so Hibernate populates the @TenantId field on the instance before it is mapped to a DTO.
        User savedAdministrator = userRepository.saveAndFlush(administrator);

        log.info("Provisioned tenant {} ({}) with administrator {}",
                savedTenant.getSlug(), savedTenant.getId(), savedAdministrator.getId());
        return new TenantRegistrationResponse(
                TenantResponse.from(savedTenant), UserResponse.from(savedAdministrator));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
