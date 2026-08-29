# TenantBase — user-service

Tenant-scoped user profiles. Consumes the tokens auth-service issues and mints none of its own.

Java 21 · Spring Boot 4.0.8 · PostgreSQL 17 · Flyway · OAuth2 Resource Server · Docker

---

## Run it

From the **repository root**:

```bash
docker compose up --build
```

| | |
|---|---|
| Swagger UI | http://localhost:8082/swagger-ui.html |
| Health | http://localhost:8082/actuator/health |

Working on just this service:

```bash
POSTGRES_HOST_PORT=5433 docker compose -f compose-dev.yaml up -d
cd user-service && DB_URL=jdbc:postgresql://localhost:5433/tenantbase_users \
  SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw spring-boot:run
```

You need a token from auth-service, so run that too. Then:

```bash
TOKEN=$(curl -sX POST localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' -d '{
  "tenantSlug":"acme","email":"admin@acme.example","password":"correct horse battery staple"}' | jq -r .accessToken)

# no profile exists yet — this creates one
curl -s localhost:8082/api/v1/profiles/me -H "Authorization: Bearer $TOKEN" | jq
```

---

## Endpoints

| Method | Path | Access | |
|---|---|---|---|
| `GET` | `/api/v1/profiles/me` | authenticated | own profile, **created on first access** |
| `PUT` | `/api/v1/profiles/me` | authenticated | replace own profile |
| `GET` | `/api/v1/profiles` | authenticated | tenant directory, paged, `?search=` |
| `GET` | `/api/v1/profiles/{userId}` | authenticated | one profile from own tenant |
| `DELETE` | `/api/v1/profiles/{userId}` | `TENANT_ADMIN` | delete profile data |

None of them takes a tenant id. The tenant comes from the token and is applied by the persistence
layer, so no payload or path can point an endpoint at another tenant's data.

---

## Design

### It verifies tokens, it does not issue them

```yaml
spring.security.oauth2.resourceserver.jwt:
  jwk-set-uri: http://auth-service:8081/.well-known/jwks.json
  issuer-uri:  http://localhost:8081
  audiences:   tenantbase-api
```

The public key is fetched once and cached, so verification is local: auth-service is **not** on this
service's request path, and a restart there does not take this service down. This service holds no
private key and cannot mint a token — which is the entire reason for signing with RSA rather than a
secret both services would have to hold.

Issuer and audience are checked alongside the signature. A token this platform signed for something
else must still be rejected here.

### Profiles are provisioned just in time

Users are created in auth-service, which cannot reach this database and has no business knowing its
schema. Three ways to get a profile row:

| | |
|---|---|
| synchronous call on registration | couples the services; sign-up fails when this one is down |
| event on a broker | the right long-term answer, but there is no broker yet |
| **just in time, from the token** | what this does |

The token makes the third safe: it is a signed assertion from auth-service that this user exists, in
this tenant, with this email. Verifying it is exactly as strong as asking auth-service directly, minus
the network call and the coupling.

When RabbitMQ arrives, a `UserCreated` consumer provisions the row earlier and this path becomes the
fallback it already is — no endpoint changes.

`GET /profiles/me` therefore never returns 404: a valid token means the user exists.

**The race is handled.** A single-page app firing two requests on load is enough for both to see no row
and both to insert. The primary key rejects the loser, and the service catches that and reads the
winner's row rather than surfacing a 500 on somebody's first ever login. Eight concurrent calls are
asserted to leave exactly one row.

### The primary key is the auth-service user id

Not a surrogate. One profile per user becomes structural — there is nowhere to put a second row — and
resolving "the profile for this caller" needs no lookup or join.

There is deliberately **no foreign key** to the users table, because it lives in another service's
database. The signed token is the integrity guarantee instead. That is weaker: a user deleted in
auth-service leaves an orphan here until an event arrives to clean it up. That is the price of services
owning their own data, and it is a real cost, not a technicality.

`email` is a **projection** of auth-service's copy, refreshed from the token on every profile read. It
exists so the directory has something to display and search, and is never authoritative — which is why
`PUT /profiles/me` has no email field.

### Tenant isolation

`UserProfile.tenantId` carries Hibernate's `@TenantId`, so `AND tenant_id = ?` is appended to every
statement and filled in on insert. `findById(userId)` is tenant-safe without the developer doing
anything — there is no predicate to forget.

`TenantContextFilter` publishes the tenant from the validated token, after the bearer-token filter.

**This service needs no equivalent of auth-service's `TenantScope`.** Hibernate fixes a session's tenant
when the session opens, which in auth-service is genuinely awkward because login must find a user before
it knows the tenant. Here the tenant arrives already validated and the filter runs before any
transaction starts, so the ordering is correct by construction.

### Errors

RFC 9457 `application/problem+json` with a stable `code`, identical to auth-service's — a client written
against one service handles the other's failures unchanged. Cross-tenant ids return **404, not 403**;
403 would confirm the row belongs to somebody.

---

## Tests

```bash
cd user-service && ./mvnw test
```

17 integration tests against a real PostgreSQL via Testcontainers.

Tokens are minted with a key pair generated for the run rather than fetched from a live auth-service.
Pointing this suite at another running service is exactly the coupling that publishing a JWKS endpoint
was meant to avoid; what matters is that the claim contract holds, and that is reproduced exactly.

`ProfileTenantIsolationIntegrationTest` is the one that matters: the same email in two tenants, a
directory that sees one of two rows, and a valid `TENANT_ADMIN` token getting 404 for a real id in
someone else's tenant.

---

## Known limits

- **Orphaned profiles.** A user deleted in auth-service leaves a row here. Needs the event consumer.
- **No avatar upload.** `avatarUrl` is a URL; object storage is out of scope.
- **Directory search is `LIKE '%term%'`**, which cannot use a B-tree index and degrades linearly with
  tenant size. Fine at the scale a tenant directory reaches; `pg_trgm` is the fix if it stops being.
- **No preferences.** A JSONB column is the obvious home for them; typed columns cover what exists today.
