package com.islamshariful.authservice.config;

import com.islamshariful.authservice.security.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Bridges {@link TenantContext} into Hibernate.
 *
 * <p>Registering this as {@code MULTI_TENANT_IDENTIFIER_RESOLVER} is what activates discriminator-based
 * multi-tenancy: from then on Hibernate adds the {@code tenant_id} restriction to every statement touching
 * an entity with a {@code @TenantId} field, and populates the column on insert.
 *
 * <p>{@code validateExistingCurrentSessions()} returns {@code false} deliberately. The default would make
 * Hibernate reject a session whose tenant changed mid-flight, and this service legitimately changes tenant
 * within a single session — {@code TenantContext.callWith(...)} during login and refresh.
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
