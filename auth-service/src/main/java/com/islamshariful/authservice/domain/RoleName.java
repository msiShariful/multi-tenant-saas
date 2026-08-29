package com.islamshariful.authservice.domain;

/**
 * The platform's fixed role catalogue.
 *
 * <p>Roles are global definitions, not per-tenant rows: every tenant draws from the same catalogue and
 * <em>membership</em> is what is tenant-scoped (via {@link User}). Tenant-defined custom roles would mean
 * moving this to a tenant-scoped table with a permission join — deliberately out of scope for the
 * boilerplate, and the reason authorities are compared by name rather than by row id.
 */
public enum RoleName {

    /** Full control over a single tenant: user provisioning and role assignment. */
    TENANT_ADMIN,

    /** Ordinary member of a tenant. */
    TENANT_USER;

    /** Spring Security compares authorities as strings; {@code ROLE_} is the prefix {@code hasRole()} adds back. */
    public String authority() {
        return "ROLE_" + name();
    }
}
