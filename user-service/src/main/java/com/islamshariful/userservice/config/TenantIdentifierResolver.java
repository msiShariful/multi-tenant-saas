package com.islamshariful.userservice.config;

import com.islamshariful.userservice.security.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Bridges {@link TenantContext} into Hibernate.
 *
 * <p>Registering this as {@code MULTI_TENANT_IDENTIFIER_RESOLVER} activates discriminator-based
 * multi-tenancy: Hibernate then appends the {@code tenant_id} restriction to every statement touching an
 * entity with a {@code @TenantId} field, and populates the column on insert. That is what makes a query
 * written without a tenant predicate safe anyway.
 *
 * <p>{@code validateExistingCurrentSessions()} returns {@code false} because the tenant is established
 * per request by {@code TenantContextFilter}; the default would reject a session whose tenant changed.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.currentTenantId();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
