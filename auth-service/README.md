# TenantBase — auth-service

Authentication, tenant provisioning and role-based access control for a multi-tenant SaaS platform.

Java 21 · Spring Boot 4.0.8 · PostgreSQL 17 · Flyway · Spring Security 7 (RS256 + JWKS) · Docker

---

## Run it

From the **repository root** (this service is one module of the TenantBase monorepo):

```bash
docker compose up --build
```

| | |
|---|---|
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| JWKS | http://localhost:8081/.well-known/jwks.json |
| Health | http://localhost:8081/actuator/health |

For development against the IDE, `cd auth-service && ./mvnw spring-boot:run` starts PostgreSQL on its own
through the root `compose-dev.yaml` (spring-boot-docker-compose) and leaves it up between restarts.

> **Port 5432 already in use?** A locally installed PostgreSQL usually owns it. The host port is
> overridable in both compose files — nothing inside the stack depends on it, since the services reach
> the database over the compose network:
>
> ```bash
> # from the repository root
> POSTGRES_HOST_PORT=5433 docker compose up --build
>
> # or, running the app outside Docker:
> POSTGRES_HOST_PORT=5433 docker compose -f compose-dev.yaml up -d
> cd auth-service && DB_URL=jdbc:postgresql://localhost:5433/tenantbase_auth \
>   SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw spring-boot:run
> ```

### A complete round trip

```bash
# 1. Provision a tenant and its first administrator
curl -sX POST localhost:8081/api/v1/tenants -H 'Content-Type: application/json' -d '{
  "tenantName": "Acme Corporation",
  "tenantSlug": "acme",
  "adminEmail": "admin@acme.example",
  "adminPassword": "correct horse battery staple",
  "firstName": "Ada", "lastName": "Lovelace"
}'

# 2. Log in — note the tenant slug is part of the credential set
TOKENS=$(curl -sX POST localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' -d '{
  "tenantSlug": "acme", "email": "admin@acme.example", "password": "correct horse battery staple"
}')
ACCESS=$(echo "$TOKENS"  | jq -r .accessToken)
REFRESH=$(echo "$TOKENS" | jq -r .refreshToken)

# 3. Use it
curl -s localhost:8081/api/v1/auth/me -H "Authorization: Bearer $ACCESS"

# 4. Rotate
curl -sX POST localhost:8081/api/v1/auth/token/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\": \"$REFRESH\"}"

# 5. Replay the token you just spent — 401, and the whole session family is revoked
curl -sX POST localhost:8081/api/v1/auth/token/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\": \"$REFRESH\"}"
```

---

## Endpoints

| Method | Path | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/tenants` | public | Provision a tenant + its administrator |
| `POST` | `/api/v1/auth/login` | public | Tenant-scoped credentials → token pair |
| `POST` | `/api/v1/auth/token/refresh` | public | Rotate a refresh token |
| `POST` | `/api/v1/auth/logout` | authenticated | Revoke the presented session |
| `GET` | `/api/v1/auth/me` | authenticated | The caller's own account |
| `POST` | `/api/v1/auth/password` | authenticated | Change own password (revokes all sessions) |
| `POST` | `/api/v1/users` | `TENANT_ADMIN` | Create a user in the caller's tenant |
| `GET` | `/api/v1/users` | `TENANT_ADMIN` | Paged listing, tenant-scoped |
| `GET` | `/api/v1/users/{id}` | `TENANT_ADMIN` | One user |
| `PUT` | `/api/v1/users/{id}/roles` | `TENANT_ADMIN` | Replace a user's roles |
| `GET` | `/.well-known/jwks.json` | public | Public signing key for downstream services |

---

## Architecture

### Multi-tenancy: shared schema, enforced by the ORM

Every tenant-scoped table carries `tenant_id`, and the entity field carries Hibernate's `@TenantId`
(`User`, `src/main/java/.../domain/User.java`). Hibernate then appends `AND tenant_id = ?` to every
select, update and delete for that entity, and fills the column on insert.

The consequence is the point of the whole design: `userRepository.findByEmail(email)` is tenant-safe
without the developer doing anything. There is no predicate to forget, no base-repository convention to
follow, no code-review checklist item. The alternative — a `tenantId` parameter threaded through every
query — works right up until one query is written without it, and that one is a data breach.

Two entities deliberately opt out:

- **`Tenant`** is the registry itself, read before any tenant is known (login resolves a slug to a tenant).
- **`RefreshToken`** is looked up by its secret on a public endpoint, before a tenant context exists. Its
  lookup key is 256 random bits, which is a stronger constraint than a tenant filter would have been.

`TenantContext` defaults to a sentinel tenant that matches no row, so an unscoped thread reads **nothing**
rather than **everything**. A forgotten scope surfaces as an empty result, never as a leak.

**Where the tenant comes from.** `TenantContextFilter` runs immediately after Spring Security's bearer-token
filter and copies the `tenant_id` claim out of the validated token. It is a claim this service signed — not
a header, not a path segment, nothing a caller can set.

<details>
<summary><b>The subtlety worth knowing about</b> — why <code>TenantScope</code> exists</summary>

Hibernate resolves the current tenant identifier **once, when the Session opens**, and pins it for that
session's lifetime. So the obvious code is wrong:

```java
@Transactional                          // session opens here, tenant = SYSTEM
public void login(...) {
    TenantContext.set(tenant.getId());  // too late — already pinned
    userRepository.findByEmail(email);  // ... AND tenant_id = SYSTEM → no rows
}
```

The ordering has to be inverted: establish the tenant, *then* start the transaction. `TenantScope` is that
one place, and it throws if a persistence context is already bound to the thread — which is also why
`spring.jpa.open-in-view` is `false`.

The same constraint shapes tenant sign-up. The administrator's `tenant_id` is written from the session's
pinned tenant, so the tenant's id must exist before the transaction opens. That is why `Tenant.create()`
assigns the UUID in application code rather than letting the database generate it: it lets both inserts
share one transaction, instead of leaving an orphaned tenant behind whenever the second insert fails.
</details>

### Tokens: asymmetric, with a published key set

| | Access token | Refresh token |
|---|---|---|
| Format | Signed JWT (RS256) | Opaque, 256 random bits |
| Lifetime | 15 minutes | 30 days |
| Storage | none — stateless | SHA-256 hash of the token |
| Revocable | no | yes, instantly |

**Why RS256 and not a shared HMAC secret.** Only auth-service should be able to *mint* tokens; everyone else
only needs to *verify* them. A symmetric secret gives both powers to every holder — a read-only service that
leaks its config can forge an admin token for any tenant. With RSA the private key never leaves this service
and peers fetch the public half from `/.well-known/jwks.json`:

```yaml
# in user-service / gateway
spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://auth-service:8081/.well-known/jwks.json
```

Verification is local, so auth-service is not on the request path of every other service and key rotation
doesn't require redeploying anybody.

**Why refresh tokens aren't JWTs.** A JWT refresh token can't be revoked without server-side state, which
defeats the reason for having one. Since state is needed anyway, the token is just random bytes and the
database row is the truth. Only its SHA-256 hash is stored — not bcrypt, because the secret has 256 bits of
entropy and there is nothing to slow down an attacker against.

**Rotation with reuse detection.** Every refresh returns a new token and consumes the old one. Tokens from
one login share a `family_id`; presenting an already-consumed token means two parties hold it, so the entire
family is revoked (OAuth 2.0 Security BCP, RFC 9700 §4.14.2). Rotation takes a `SELECT … FOR UPDATE` on the
row so two concurrent refreshes cannot both succeed and fork the chain.

**What short access-token TTLs buy.** Nothing can recall an issued access token — a removed role or a
disabled account stays effective until it expires. Fifteen minutes is the bound on that window, and the
`roleChangesApplyAtTheNextTokenIssue` test asserts exactly this behaviour rather than pretending otherwise.

### Layering

```
web/          controllers — HTTP, status codes, OpenAPI. No business rules.
service/      transactions, business rules, tenant scoping
repository/   Spring Data JPA
domain/       entities; behaviour lives on them (User.registerFailedLogin, RefreshToken.rotateTo)
dto/          request/response records — entities never cross the controller boundary
security/     token issuance, principal, tenant propagation
config/       beans and typed configuration properties
exception/    typed API exceptions + one RFC 9457 handler
```

`UserResponse` omits `passwordHash`, `failedLoginAttempts` and `lockedUntil`. Returning the entity would
have published all three.

### Errors — RFC 9457 `application/problem+json`

```json
{
  "type": "https://tenantbase.dev/problems/invalid-credentials",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Invalid tenant, email or password",
  "instance": "/api/v1/auth/login",
  "code": "INVALID_CREDENTIALS",
  "timestamp": "2026-08-28T10:15:30Z"
}
```

Branch on `code`; `detail` is prose and may be reworded. Validation failures add an `errors` array of
`{field, message}`; unexpected failures add a `traceId` that is also written to the log line.

`AuthenticationException` and `AccessDeniedException` are raised inside the servlet filter chain, upstream of
`DispatcherServlet`, so a plain `@RestControllerAdvice` never sees them — which is how APIs end up with two
different error shapes. `ProblemDetailAuthenticationHandlers` routes them back through the MVC exception
resolver, so there is exactly one.

The shape is documented, not just implemented. `ProblemResponseCustomizer` attaches the `ApiError` schema to
every 4xx/5xx in the OpenAPI document, and adds the responses that are true of an operation by construction —
401 wherever a token is required, 400 wherever there is a body to validate. Applying that to the assembled
document rather than to twenty individual methods means a new endpoint cannot ship undocumented errors by
forgetting an annotation.

`ApiError` is a documentation model only — nothing constructs it, because errors are real `ProblemDetail`
instances whose extension members live in an untyped property map no schema generator can see. That invites
drift, so `ProblemDetailContractTest` provokes a real failure per handler branch and fails the build if any
response carries a member `ApiError` does not declare.

### Security decisions

| Decision | Reasoning |
|---|---|
| No `UserDetailsService` | Its contract is `loadUserByUsername(String)`; an identity here is `(tenant, email)`. Encoding the tenant into the username string or stashing it in a `ThreadLocal` are both worse than verifying the password in a service method. |
| Delegating password encoder | Hashes carry their algorithm id (`{bcrypt}…`), so moving to Argon2 later upgrades each hash on its owner's next login instead of requiring a reset-everyone migration. |
| Length-only password rule (min 12) | NIST SP 800-63B: mandated character mixes produce predictable substitutions without adding entropy. |
| Identical response for unknown tenant / unknown user / wrong password | Otherwise login is an account-enumeration oracle. A dummy hash verification on the miss path equalises the timing too. |
| Cross-tenant reads return 404, not 403 | 403 would confirm the id belongs to *someone*. |
| Per-account lockout after 5 failures | Stops credential stuffing against a known address. Per-IP rate limiting belongs at the gateway, before a request costs a bcrypt verification. |
| CSRF disabled | CSRF defends session cookies. There are none: no session, no cookie, and browsers do not attach `Authorization` cross-site. |
| Last administrator cannot be demoted | Otherwise a tenant can lock itself out permanently and recovery needs a database edit. |
| `management.endpoints.web.exposure.include: health,info` | `*` is one misconfigured ingress away from publishing `/actuator/env`, credentials included. |

### Schema

`tenants` → `users` (unique on `tenant_id, email`) → `user_roles` → `roles`; `refresh_tokens` referencing
both tenant and user. Flyway owns it (`src/main/resources/db/migration/`), and
`spring.jpa.hibernate.ddl-auto=validate` fails startup if the entities and the migrations disagree — it
caught a `CHAR(64)` vs `VARCHAR(64)` drift while this was being written.

Email is unique **per tenant**, not globally: one person may hold accounts in several tenants. Making it
globally unique is a very expensive thing to undo once real accounts exist.

---

## Signing keys

Without configured keys the service generates a throwaway RSA pair at startup and logs a warning. That keeps
`docker compose up` a single command; it also means tokens do not survive a restart and two replicas will not
agree. Generate a real pair for anything else:

```bash
mkdir -p infra/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out infra/keys/app.key
openssl rsa -in infra/keys/app.key -pubout -out infra/keys/app.pub
```

Then uncomment the volume and the two `TENANTBASE_SECURITY_JWT_*_KEY_LOCATION` variables in the root
`compose.yaml`. `infra/keys/` is git-ignored. In production these belong in a secret manager, mounted read-only.

---

## Tests

```bash
cd auth-service && ./mvnw test
```

27 integration tests against a real PostgreSQL via Testcontainers — not H2, because the behaviour most worth
testing lives in the SQL Hibernate generates, and the same container proves the migrations apply.

The tests that would matter if they regressed are in `TenantIsolationIntegrationTest`: the same email holding
accounts in two tenants, a listing that sees one of two administrators, and a valid `TENANT_ADMIN` token
getting a 404 for a real id in someone else's tenant.

There is no `@Transactional` rollback-per-test. It would bind a persistence context to the thread before the
request ran, which `TenantScope` correctly refuses to work under; tables are truncated between tests instead.

---

## Configuration

Everything below is an environment variable with a working default.

| Variable | Default | |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tenantbase_auth` | |
| `DB_USERNAME` / `DB_PASSWORD` | `tenantbase` | |
| `JWT_ISSUER` | `http://localhost:8081` | must match downstream config |
| `JWT_AUDIENCE` | `tenantbase-api` | |
| `JWT_ACCESS_TTL` | `15m` | |
| `JWT_REFRESH_TTL` | `30d` | |
| `LOGIN_MAX_ATTEMPTS` | `5` | |
| `LOGIN_LOCKOUT` | `15m` | |
| `TENANTBASE_SECURITY_JWT_PRIVATE_KEY_LOCATION` | *(unset)* | e.g. `file:/run/secrets/jwt/app.key` |
| `TENANTBASE_SECURITY_JWT_PUBLIC_KEY_LOCATION` | *(unset)* | e.g. `file:/run/secrets/jwt/app.pub` |

---

## Known limits

Stated rather than hidden — each is a deliberate stopping point, not an oversight.

- **Tenant suspension takes up to one access-token TTL to bite.** Checking tenant status per request costs a
  database round trip on every call; the 15-minute window is the trade. A Redis-backed revocation list is the
  fix when it stops being acceptable.
- **Key rotation requires a restart.** `ImmutableJWKSet` holds one key. Publishing two keys and signing with
  the newer is the upgrade; it needs somewhere to store keys first.
- **No email verification or password reset.** Both need an outbound mail path, which is the RabbitMQ phase.
- **Revoked-but-unexpired refresh tokens accumulate.** `RefreshTokenRepository.deleteExpiredBefore` exists but
  nothing schedules it yet; it wants to be a job, not a `@Scheduled` method on every replica.
- **Roles are a fixed catalogue.** Tenant-defined roles need a tenant-scoped role table and a permission join.
- **UUIDv4 primary keys** scatter across the B-tree instead of appending. Immaterial at tenant and user
  volumes; UUIDv7 is the upgrade if it stops being.

## Next

`user-service` consumes this one's tokens: point it at the JWKS URL, read `tenant_id` from the validated JWT,
and apply the same `@TenantId` mapping. The two share only a user id — profile data belongs there,
credentials and roles stay here, and each owns its own database.

See the [repository README](../README.md) for how the services fit together.
