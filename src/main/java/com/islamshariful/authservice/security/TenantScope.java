package com.islamshariful.authservice.security;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work inside a transaction that belongs to a specific tenant.
 *
 * <p><b>Why this exists.</b> Hibernate resolves the current tenant identifier <em>once, when the Session is
 * opened</em>, and pins it for the session's lifetime. So this does not work:
 *
 * <pre>{@code
 * @Transactional                                   // session opens here, tenant = SYSTEM
 * public void login(...) {
 *     TenantContext.set(tenant.getId());           // too late; the session is already pinned
 *     userRepository.findByEmail(email);           // ... AND tenant_id = SYSTEM -> no rows
 * }
 * }</pre>
 *
 * <p>The ordering has to be inverted: set the tenant, <em>then</em> start the transaction. Wrapping that in
 * one place keeps it from being rediscovered — and quietly got wrong — in every flow that authenticates
 * before it has a tenant.
 *
 * <p>The guard is not paranoia. If a persistence context is already bound to the thread, the template joins
 * it and silently reuses its pinned tenant, which is precisely the cross-tenant read this class exists to
 * prevent. Failing loudly turns that into a test failure instead of a security incident. It also means
 * {@code spring.jpa.open-in-view} must stay {@code false}, which it should be regardless.
 */
@Component
public class TenantScope {

    private final TransactionTemplate transactionTemplate;
    private final EntityManagerFactory entityManagerFactory;

    public TenantScope(PlatformTransactionManager transactionManager, EntityManagerFactory entityManagerFactory) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManagerFactory = entityManagerFactory;
    }

    public <T> T execute(UUID tenantId, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
            throw new IllegalStateException(
                    "TenantScope.execute must open its own transaction: a persistence context is already bound to "
                            + "this thread and would keep its original tenant identifier");
        }
        return TenantContext.callWith(tenantId, () -> transactionTemplate.execute(status -> work.get()));
    }

    public void run(UUID tenantId, Runnable work) {
        execute(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
