# TenantBase — working notes for Claude

Multi-tenant SaaS backend. Monorepo of independently deployable Spring Boot services.
Java 21 · Spring Boot 4.0.8 · PostgreSQL 17 · Flyway · Spring Security 7 · Docker.

**This is a portfolio project.** The code is read as a work sample, so explain non-obvious decisions
where they are made, name the alternative that was rejected, and state known limits rather than hiding
them. Say so plainly when something would not pass a senior code review.

```
compose.yaml          whole platform          compose-dev.yaml   infra only, for IDE work
infra/postgres/        one database per service
auth-service/          port 8081 — tenants, credentials, roles, tokens, JWKS
user-service/          port 8082 — profiles, provisioned just in time from the token
gateway/               port 8080 — not built yet
```

## Commands

Run Maven from inside the service directory, compose from the repo root.

```bash
cd auth-service && ./mvnw test          # 29 integration tests, needs Docker (Testcontainers)
cd user-service && ./mvnw test          # 17
cd auth-service && ./mvnw spring-boot:run
docker compose up --build               # whole stack
docker compose -f compose-dev.yaml up -d   # database only
```

**A locally installed PostgreSQL owns port 5432 on this machine.** Everything must be published
elsewhere or it fails with `address already in use`:

```bash
POSTGRES_HOST_PORT=5433 docker compose -f compose-dev.yaml up -d
cd auth-service && DB_URL=jdbc:postgresql://localhost:5433/tenantbase_auth \
  SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw spring-boot:run
```

## Invariants — these break silently

**Hibernate pins the tenant when the Session opens, not per query.** So setting the tenant inside a
`@Transactional` method is too late and reads return nothing. Anything that must authenticate before it
knows its tenant (login, refresh) goes through `TenantScope.execute(tenantId, …)`, which establishes
the tenant *then* opens the transaction. It throws if a persistence context is already bound.

**`spring.jpa.open-in-view` must stay `false`.** Enabling it binds a persistence context before
`TenantScope` can set the tenant, which is a cross-tenant read, not just a performance smell.

**Ids are assigned in the application**, not by the database — `Tenant.create()` does
`UUID.randomUUID()`. That is what lets tenant sign-up insert the tenant and its administrator in one
transaction: the admin's `tenant_id` comes from the session's pinned tenant, so the tenant id must
exist before the transaction starts. Consequently `AuditableEntity.version` is a boxed `Long`, so
Spring Data's `isNew()` consults the version rather than the id and `save()` issues an insert instead
of a select-then-insert.

**`@TenantId` goes on tenant-scoped entities only.** `Tenant` is the registry and must be readable
before any tenant context exists. `RefreshToken` is looked up by its secret on a public endpoint before
a tenant is known — its `tenantId` is a plain column read *from* the row.

**user-service needs no `TenantScope`, and that is deliberate.** Its tenant arrives already validated
in the token and `TenantContextFilter` publishes it before any transaction opens, so the ordering is
correct by construction. Do not "port it for consistency" — it would be dead machinery.

**A profile's primary key is the auth-service user id.** One profile per user is structural, not a
rule. There is no foreign key to `users` because it is in another service's database; the signed token
is the integrity guarantee, and orphans are a known consequence awaiting the event consumer.

**Login must not throw inside its own transaction.** `attemptLogin` returns the failure instead, and
the caller re-throws after commit. Throwing would roll back the failed-attempt counter and the lockout
would never engage.

## Conventions

- Controller → Service → Repository. Entities never cross the controller boundary; DTOs are records.
- Errors are RFC 9457 `ProblemDetail` with a stable `code`. `GlobalExceptionHandler` is the only place
  that shapes them; security failures are routed back into it by `ProblemDetailAuthenticationHandlers`.
- Every schema change is a Flyway migration. `ddl-auto: validate` — never `update`.
- All time comes from the injected `Clock`, never `Instant.now()`.
- Conventional Commits (`feat(scope):`, `fix:`). No `Co-Authored-By` trailer.
- New endpoints inherit the problem+json error schema from `ProblemResponseCustomizer`; declare the
  success response explicitly, because adding an `@ApiResponse` makes springdoc drop the inferred one.

## Spring Boot 4 traps already hit here

- **Flyway needs `spring-boot-starter-flyway`.** Bare `flyway-core` puts migrations on the classpath
  without the auto-configuration, so nothing runs and Hibernate fails on a missing table.
- Packages moved: `HibernatePropertiesCustomizer` → `org.springframework.boot.hibernate.autoconfigure`;
  `AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`;
  `BearerTokenAuthenticationFilter` → `…oauth2.server.resource.web.authentication`.
- `spring.datasource.hikari.connection-timeout` is a raw long in **milliseconds**, not a Duration.
- `${VAR:#{null}}` does not yield null for a `Resource` property — it binds the literal `#{null}`.
- Testcontainers 2.x: `testcontainers-postgresql`, class `org.testcontainers.postgresql.PostgreSQLContainer`.

Verify a package or coordinate against the actual jar (`unzip -l`, `javap`) before writing code around
it. Boot 3 muscle memory is wrong often enough to matter.

## Testing

Integration tests run against real PostgreSQL via Testcontainers, because the behaviour worth testing —
the tenant predicate Hibernate adds to every statement — lives in generated SQL. **No `@Transactional`
on tests**: a rollback-per-test binds a persistence context before the request runs, which `TenantScope`
correctly refuses. Tables are truncated between tests instead.

`ProblemDetailContractTest` fails the build if an error response carries a member `ApiError` does not
document. `TenantIsolationIntegrationTest` is the suite that matters most.

## Don't

- Extract a shared **domain** library between services — that is how a distributed monolith gets built.
  Sharing technical infrastructure (tenant plumbing, error handling) is fine once a third service needs
  it; until then duplicate it.
- Let one service read another's database. Each owns its own, created by `infra/postgres/init-db.sql`.
- Widen `management.endpoints.web.exposure.include` beyond `health,info`.
- Return 403 for a cross-tenant id. It is 404 — 403 confirms the row exists.
