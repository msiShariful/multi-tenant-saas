# TenantBase

A multi-tenant SaaS backend built as Spring Boot microservices — tenant provisioning, authentication,
role-based access control, and data isolation that is enforced by the persistence layer rather than by
convention.

Java 21 · Spring Boot 4.0.8 · PostgreSQL 17 · Flyway · Spring Security 7 (RS256 + JWKS) · Docker

```bash
docker compose up --build
```

| | |
|---|---|
| **the API** | http://localhost:8080 — everything routes through here |
| auth-service · Swagger UI | http://localhost:8081/swagger-ui.html |
| user-service · Swagger UI | http://localhost:8082/swagger-ui.html |
| JWKS | http://localhost:8080/.well-known/jwks.json |

> **`address already in use` on 5432?** A locally installed PostgreSQL usually owns it. Publish it
> elsewhere — nothing inside the stack depends on the mapping:
> `POSTGRES_HOST_PORT=5433 docker compose up --build`

---

## Services

| | port | what it owns |
|---|---|---|
| [**gateway**](gateway/) | 8080 | one address for the API — routing and CORS |
| [**auth-service**](auth-service/) | 8081 | tenants, credentials, roles, token issuance, JWKS |
| [**user-service**](user-service/) | 8082 | user profiles, provisioned just in time from the token |
| [frontend](frontend/) | 3000 | Next.js — scaffold only, see its README for the plan |

Each service is an independent Spring Boot application with **its own database**, its own Flyway
migrations, and its own Dockerfile. They are deployed separately and share no schema.

## Why a monorepo

One repository, many independently deployable services — the repo boundary and the deployment boundary
are different things. Nothing here is a distributed monolith: no service reads another's tables, and no
service imports another's domain model.

What one repository buys at this size is that a change to a cross-service contract — a JWT claim, an
error shape — is one commit, one CI run, one revert, instead of coordinated pull requests across
repositories with a window where the system is broken. Per-repo isolation solves team-scale problems
this project does not have.

The one thing to resist is the shared domain library. Sharing *technical* infrastructure once a third
service needs it is fine; sharing *entities* couples every release to every other and is how a
distributed monolith actually gets built.

## How the services fit together

```
                    ┌──────────────┐
   credentials ────▶│ auth-service │────▶ access token (RS256, signed)
                    └──────┬───────┘       claims: sub, tenant_id, roles
                           │
                  /.well-known/jwks.json
                           │  public key, fetched once and cached
                           ▼
                    ┌──────────────┐
   access token ───▶│ user-service │  verifies locally — no call back to auth-service
                    └──────────────┘
```

**Tokens are asymmetrically signed on purpose.** Only auth-service can mint one; everyone else only
needs to verify. A shared HMAC secret would give both powers to every holder, so any service that leaked
its configuration could forge an administrator token for any tenant.

**The gateway does not validate tokens; the services do.** The services are directly reachable, so a
gateway that were the only thing checking would make its bypass a full compromise. It routes, applies
CORS, and keeps the topology private — and each service independently verifies signature, issuer and
audience against auth-service's published key.

**Every access token carries a `tenant_id` claim**, and each service scopes its own queries to it. In
auth-service that scoping is applied by Hibernate's `@TenantId` in the entity mapping, not by predicates
a developer has to remember — so a query written without a tenant filter still cannot read across
tenants. An id from another tenant reads as `404`, never `403`.

## Repository layout

```
multi-tenant-saas/
├── compose.yaml               # the whole platform
├── compose-dev.yaml           # infrastructure only, for running a service from the IDE
├── infra/postgres/init-db.sql # one database per service
├── gateway/                   # the edge — routing and CORS
├── auth-service/              # own pom, Dockerfile, migrations, tests
├── user-service/              # same, and a database auth-service cannot reach
└── frontend/                  # Next.js, acting as a BFF — scaffold only
```

The frontend is in this repository for the same reason the services are: the API contract now crosses a
language boundary, so a change to a response shape is a Java change *and* a TypeScript change. In one
repository that is one commit and one revert. No compiler catches that mismatch across two.

### How a profile comes into existence

Users are created in auth-service, which cannot reach user-service's database. Rather than a
synchronous call on registration — which would couple the two and make sign-up fail whenever
user-service is down — a profile is materialised **on first access** from the claims of the token the
caller already holds. The signature makes that safe: it is auth-service asserting that this user exists,
in this tenant, with this email.

When RabbitMQ arrives, a `UserCreated` consumer provisions the row earlier and the just-in-time path
becomes the fallback it already is. No endpoint changes.

## Working on one service

```bash
POSTGRES_HOST_PORT=5433 docker compose -f compose-dev.yaml up -d   # database only
cd auth-service && ./mvnw spring-boot:run
```

Each service's own README covers its endpoints, design decisions and known limits:
[auth-service](auth-service/README.md), [user-service](user-service/README.md).

## Tests

```bash
cd auth-service && ./mvnw test    # 29 tests
cd user-service && ./mvnw test    # 17 tests
cd gateway      && ./mvnw test    # 3  tests
```

The frontend has no tests yet — it has no features yet.

Integration tests run against a real PostgreSQL via Testcontainers rather than an in-memory database,
because the behaviour most worth testing — the tenant predicate Hibernate adds to every statement —
lives in generated SQL.
