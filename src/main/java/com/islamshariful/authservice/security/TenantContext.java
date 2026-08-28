package com.islamshariful.authservice.security;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the tenant the current thread is acting for.
 *
 * <p>Hibernate reads this through {@code CurrentTenantIdentifierResolver} on every statement, so whatever
 * is set here decides which rows exist as far as the persistence layer is concerned.
 *
 * <p>The default is {@link #SYSTEM_TENANT}, an id that matches no tenant row. That choice is the important
 * one: an unscoped thread sees <em>nothing</em> rather than <em>everything</em>. A forgotten scope becomes
 * an empty result and a failing test, never a cross-tenant leak.
 */
public final class TenantContext {

    /**
     * Sentinel used when no tenant has been established. Never persisted, and guaranteed by the
     * {@code gen_random_uuid()} defaults to match no real tenant.
     */
    public static final UUID SYSTEM_TENANT = new UUID(0L, 0L);

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    /** The active tenant, or {@link #SYSTEM_TENANT} when the thread has not been scoped. */
    public static UUID currentTenantId() {
        UUID tenantId = CURRENT.get();
        return tenantId != null ? tenantId : SYSTEM_TENANT;
    }

    public static boolean isScoped() {
        return CURRENT.get() != null;
    }

    public static void set(UUID tenantId) {
        if (tenantId == null || SYSTEM_TENANT.equals(tenantId)) {
            throw new IllegalArgumentException("Refusing to scope a thread to a null or system tenant");
        }
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs {@code action} scoped to {@code tenantId} and restores the previous scope afterwards.
     *
     * <p>Used by flows that must establish a tenant before they can query it — login resolves the tenant by
     * slug first, refresh reads it off the token row — and by anything running outside a request thread.
     */
    public static <T> T callWith(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    public static void runWith(UUID tenantId, Runnable action) {
        callWith(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
