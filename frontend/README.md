# TenantBase — frontend

Next.js 16 (App Router) · React 19 · TypeScript · Tailwind 4

**Status: scaffold only.** No features are implemented. This document is the plan for what goes here
and, more importantly, *why the shape is what it is* — the token-handling decision below constrains
everything built on top of it, so it is worth settling before the first feature.

```bash
cd frontend
npm run dev      # http://localhost:3000
npm run build
```

The backend is not required to boot the UI, but is required for anything to work:

```bash
# from the repository root
POSTGRES_HOST_PORT=5433 docker compose -f compose-dev.yaml up -d
cd auth-service && ./mvnw spring-boot:run    # :8081
cd user-service && ./mvnw spring-boot:run    # :8082
```

---

## The shape: this app is the BFF

A **Backend For Frontend** is a server layer belonging to one specific client, sitting between it and
the services. With Next.js you do not build one — the App Router's Route Handlers already run on the
server, so the layer exists in the app you were going to write anyway.

The browser talks only to Next.js. Next.js talks to the services.

```
                     ┌──────────────────────────────┐
  browser ──────────▶│  Next.js  (this app)         │
   httpOnly cookie   │  ─ Route Handlers            │──Bearer──▶ auth-service :8081
   no tokens in JS   │  ─ holds the token pair      │──Bearer──▶ user-service :8082
                     └──────────────────────────────┘
```

### Why not just keep tokens in the browser

The obvious approach is to call `auth-service` directly from the browser and put the token pair in
`localStorage`. It works, and it is what most tutorials do. It is also the wrong trade here:

- **`localStorage` is readable by any script on the page.** One XSS — a bad dependency, an unescaped
  render — and the attacker has both tokens.
- **The refresh token is the expensive one.** It is valid for 30 days and rotating it is how a session
  survives. An attacker who exfiltrates it has the account until somebody notices, from anywhere.
- An `httpOnly` cookie **cannot be read by JavaScript at all**. XSS can still act as the user while they
  are on the page, but it cannot steal a credential and use it later from somewhere else. That is a
  large reduction in blast radius for a small amount of work.

### What else falls out of it

- **CORS stops existing.** The browser only ever calls its own origin. Right now both services declare
  `.cors(Customizer.withDefaults())` with no `CorsConfigurationSource` bean, which is a no-op — a
  browser on `:3000` calling `:8081` would be blocked on the preflight. Routing through Next.js means
  that never has to be solved.
- **Refresh becomes invisible.** The server rotates the token before proxying, so the browser never sees
  a 401 or a retry, and never races two refreshes against auth-service's reuse detection.
- **Service topology stays private.** The browser learns one origin. Ports, service names and the split
  between auth-service and user-service are not public API.

### Not the same thing as the planned gateway

| | |
|---|---|
| **Spring Cloud Gateway** (roadmap) | infrastructure edge for *all* clients — routing, rate limiting, TLS |
| **BFF** (this app) | application layer for *one* client, shaped to what this UI needs |

They coexist; the BFF calls through the gateway once it exists.

---

## Plan

### Phase 1 — session plumbing

The decision that matters: **auth-service needs no changes.** Next.js receives the JSON token pair
server-side, keeps it, and issues its own encrypted `httpOnly` session cookie to the browser. The
alternative — teaching auth-service to set cookies — couples it to one client's transport and would mean
reworking a token flow that is already tested.

| route | does |
|---|---|
| `POST /api/session` | takes `{tenantSlug, email, password}`, calls auth-service, stores the pair, sets the cookie |
| `DELETE /api/session` | calls `/auth/logout`, clears the cookie |
| `GET /api/session` | returns the current user for the UI, or 401 |

A server-only `apiFetch()` helper attaches the access token, and on 401 rotates the refresh token once
and retries. Refresh must be **serialised per session** — two concurrent rotations would present the
same refresh token twice, and auth-service treats a replayed token as a compromise and revokes the whole
family. Getting this wrong logs the user out at random, which is a miserable bug to diagnose.

Session storage: an encrypted cookie holding the pair is enough at this size. It has a real limit —
4 KB total, and the access token alone is ~800 bytes — so if claims grow, this moves to a server-side
store keyed by an opaque cookie.

### Phase 2 — the screens

| | uses |
|---|---|
| Sign in | `POST /api/session` |
| Sign up | `POST /api/v1/tenants` — creates tenant + first admin |
| Profile | `GET`/`PUT` user-service `/profiles/me` |
| Directory | `GET /profiles?search=` |
| Admin: users | auth-service `/users`, role assignment |

### Phase 3 — the things that make it a portfolio piece

- **Generate TypeScript types from the OpenAPI documents** both services already publish, rather than
  hand-writing interfaces that drift. The contract is already machine-readable; not using it is a
  wasted asset.
- **Render the 404-not-403 behaviour honestly.** A cross-tenant id returns "not found" and the UI must
  say exactly that, not "forbidden" — otherwise the frontend leaks what the backend deliberately hides.
- **Surface `code`, not `detail`.** Errors are RFC 9457 with a stable `code`; branch on it and map to
  copy, so re-wording a backend message never changes UI behaviour.
- Optimistic-locking conflicts (409 `CONCURRENT_MODIFICATION`) need a real "someone else changed this"
  path, not a generic toast.

---

## Known gaps in the plan

- **CSRF comes back.** Cookie auth is exactly the thing CSRF exists for, and the services disabled CSRF
  because they were bearer-only. The BFF's mutating routes need protection — `SameSite=Lax` plus origin
  checking is the cheap version; a token pattern is the thorough one.
- **No token is a session.** Next.js holding tokens means a Next.js restart drops them unless they live
  in the cookie or a shared store. Fine for one instance, not for several.
- **The BFF is a hop.** Every request pays it. Immaterial here; worth knowing.
