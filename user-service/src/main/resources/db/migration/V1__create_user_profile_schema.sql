-- ---------------------------------------------------------------------------
-- TenantBase :: user-service baseline schema
--
-- Same tenancy model as auth-service: shared database, shared schema, tenant_id
-- discriminator, with isolation enforced by Hibernate's @TenantId rather than by
-- hand-written predicates.
--
-- This database is NOT auth-service's. Neither service holds a connection to the
-- other's data, so the isolation between them is enforced by the connection each
-- is given rather than by convention.
-- ---------------------------------------------------------------------------

CREATE TABLE user_profiles
(
    -- The auth-service user id (the token's `sub`), not a surrogate of our own. One profile per user
    -- is therefore structural: there is nowhere to put a second row.
    --
    -- No foreign key to users: that table lives in another service's database. The signed token is the
    -- integrity guarantee instead, which is weaker and is the cost of services owning their own data.
    id           UUID         NOT NULL,
    tenant_id    UUID         NOT NULL,
    -- Projection of auth-service's email, refreshed from the token. Never authoritative; it exists so
    -- the directory has something to display and search.
    email        VARCHAR(320) NOT NULL,
    display_name VARCHAR(150),
    bio          VARCHAR(1000),
    avatar_url   VARCHAR(2048),
    phone_number VARCHAR(30),
    time_zone    VARCHAR(64),
    locale       VARCHAR(35),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_profiles PRIMARY KEY (id)
);

-- Every tenant-scoped query filters on tenant_id first.
CREATE INDEX idx_user_profiles_tenant_id ON user_profiles (tenant_id);

-- Supports the directory listing's ordering within a tenant.
CREATE INDEX idx_user_profiles_tenant_created ON user_profiles (tenant_id, created_at DESC);
