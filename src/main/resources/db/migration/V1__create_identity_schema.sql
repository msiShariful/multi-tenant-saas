-- ---------------------------------------------------------------------------
-- TenantBase :: auth-service baseline schema
--
-- Tenancy model: shared database, shared schema, `tenant_id` discriminator.
-- Chosen over database-per-tenant (operationally expensive past a few dozen
-- tenants: N connection pools, N migration runs) and schema-per-tenant
-- (Postgres degrades once the catalogue holds thousands of schemas).
-- The trade-off accepted is that isolation is enforced by the application
-- rather than the engine -- which is exactly why it is enforced in Hibernate's
-- SQL generation (@TenantId) and never left to hand-written predicates.
-- ---------------------------------------------------------------------------

CREATE TABLE tenants
(
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    slug       VARCHAR(63)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    -- Slugs land in URLs and login payloads; keep them boring and unambiguous.
    CONSTRAINT ck_tenants_slug_format CHECK (slug ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$')
);

CREATE TABLE roles
(
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE users
(
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID         NOT NULL,
    email                 VARCHAR(320) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    first_name            VARCHAR(100),
    last_name             VARCHAR(100),
    status                VARCHAR(20)  NOT NULL,
    last_login_at         TIMESTAMPTZ,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    -- Email is unique *per tenant*, not globally: one person may hold accounts in
    -- several tenants, which is why login is (tenant slug, email, password).
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- Every tenant-scoped query filters on tenant_id first; lead with it.
CREATE INDEX idx_users_tenant_id ON users (tenant_id);

CREATE TABLE user_roles
(
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

CREATE TABLE refresh_tokens
(
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL,
    user_id        UUID        NOT NULL,
    -- SHA-256 hex of the token handed to the client; the plaintext is never persisted.
    token_hash     CHAR(64)    NOT NULL,
    -- All tokens produced by one login share a family; reuse revokes the family.
    family_id      UUID        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id UUID,
    user_agent     VARCHAR(255),
    ip_address     VARCHAR(45),
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
