# TenantBase — gateway

One address for the platform. Routes to auth-service and user-service, applies CORS, and keeps the
topology behind it private.

Java 21 · Spring Boot 4.0.8 · Spring Cloud 2025.1.3 · Spring Cloud Gateway 5.0.3 (servlet)

```bash
# from the repository root
docker compose up --build          # everything on :8080

# or locally, with the services already running on 8081/8082
cd gateway && ./mvnw spring-boot:run
```

## Routes

| path | goes to |
|---|---|
| `/api/v1/auth/**` | auth-service |
| `/api/v1/tenants/**` | auth-service |
| `/api/v1/users/**` | auth-service — *identity administration* |
| `/api/v1/profiles/**` | user-service |
| `/.well-known/jwks.json` | auth-service |

`/api/v1/users` and `/api/v1/profiles` are close enough in name to be worth stating plainly: the first
is credentials, status and role assignment, which auth-service owns. The second is display data, which
user-service owns. Routing either to the wrong place produces 404s that look like a data problem.

JWKS is routed so a client that knows only the gateway can still verify tokens offline without being
told auth-service's address.

## Decisions

### The servlet gateway, not the reactive one

Spring Cloud Gateway ships both. `spring-cloud-starter-gateway-server-webmvc` is the servlet variant,
and it is what Initializr's plain "Gateway" now selects.

The historical reason to insist on the reactive gateway was that proxying blocks a thread per in-flight
request, which is ruinous on a platform-thread pool. **Java 21 virtual threads remove that argument** —
a parked virtual thread costs almost nothing — and both services already run servlet stacks with
`spring.threads.virtual.enabled: true`. Choosing the reactive gateway would introduce a second
programming model, with different debugging and different failure modes, to solve a problem that no
longer exists at this scale.

At genuinely high throughput, reactive still wins. That is not this.

### The gateway does not validate tokens

It routes; the services validate. Two reasons:

- **The services are directly reachable.** Ports 8081 and 8082 are published, and in a real deployment
  something inside the network can always reach them. A gateway that is the only thing checking tokens
  is a gateway whose bypass is a full compromise.
- **They already validate properly** — signature, issuer and audience, against auth-service's published
  key. Repeating that here would duplicate the work without adding a guarantee.

Coarse edge checks that *are* worth adding here — rate limiting, request size caps — are listed below.

### CORS lives here

One policy for the platform, in one place, so no service can drift by forgetting to configure it.

Implemented as a `CorsFilter` bean, not configuration properties: the servlet gateway publishes no CORS
property keys, and the natural-looking `spring.web.cors.*` **does not exist**. A block there binds to
nothing, starts cleanly, and simply never answers a preflight. Origins are listed exactly — never `*`,
which the browser rejects alongside credentials anyway and which would hand an authenticated session to
any site on the internet.

The Next.js BFF does not need any of this: it calls from the server, where CORS does not apply. This is
for browser clients addressing the gateway directly.

## Debugging routes

There is **no** `/actuator/gateway/routes` — the servlet gateway ships no actuator endpoint, unlike the
reactive one. Raise the log level instead:

```bash
GATEWAY_LOG_LEVEL=TRACE ./mvnw spring-boot:run
```

It prints the parsed route table and each route's combined predicate at startup:

```
Combined predicate for route profiles - /api/v1/profiles/**
```

which answers what the gateway actually parsed, as opposed to what the YAML looks like — usually the
cause of an unexpected 404.

## Not done yet

- **Rate limiting.** The obvious next addition, and the reason to have an edge at all beyond routing.
  Spring Cloud Gateway's `RequestRateLimiter` ships a Redis-backed implementation, so this means adding
  Redis to compose. Per-tenant and per-IP keys both matter: per-IP protects the login endpoint from
  credential stuffing, per-tenant stops one customer starving the others.
- **Request size caps and timeouts** — cheap, no new infrastructure, and currently absent.
- **No service discovery.** Service addresses are configuration. Eureka or Kubernetes DNS is what
  replaces that, and neither is worth it for two fixed services.
- **No TLS.** Terminated by whatever runs in front in a real deployment; HSTS is disabled downstream for
  the same reason.
