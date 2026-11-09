# ADR 004: API gateway, zero-trust JWT checks and Redis rate limiting

- Status: Accepted
- Date: 2026-11-08

## Context

The SPA talks to four backend services. Without a single entry point, every browser client would
need to know four hostnames, each service would implement its own throttling, and a request could
reach a service with an identity the client invented. nginx serves the static client and proxies
`/api` to one address, so that address has to be the gateway.

The gateway is the first line of defence, not the only one. A request that bypasses it (a service
reachable from another container, a misconfigured port) must still be rejected unless it carries a
valid token. The deployment is a single Docker Compose stack, not a fleet with frequent instance
changes.

## Decision

### Routing and what is not exposed

| Path prefix | Upstream | Default URI | Auth |
|---|---|---|---|
| `/api/auth/**`, `/api/platform/**` | auth-service | `http://localhost:8081` | token, except the public auth routes |
| `/api/courses/**`, `/api/modules/**`, `/api/lessons/**`, `/api/enrollments/**`, `/api/stream/**` | course-service | `http://localhost:8082` | token |
| `/api/payments/**` | payment-service | `http://localhost:8083` | token |

1. Route URIs come from `AUTH_SERVICE_URI`, `COURSE_SERVICE_URI` and `PAYMENT_SERVICE_URI`. The
   defaults target a local run where the services start from the IDE.
2. Public routes are exactly `POST /api/auth/login|register|refresh|logout`,
   `GET /api/auth/tenants/*/branding` and `/api/stream/media/**`. Media requests carry a short-lived
   playback token that course-service validates; the gateway deliberately does not, because that
   token has a different audience and signing secret.
3. There is no route for notification-service and no route that exposes Actuator. A request to
   `/api/notifications/**` or `/actuator/**` has no handler and returns 404. The security filter
   chain only matches the routed API prefixes, so those paths are not turned into 401 responses
   either.
4. `/actuator/prometheus` is served on the separate management port (9090 locally) with the common
   tag `application=gateway`. Tenant identifiers are never used as metric tags; a per-tenant tag
   would create unbounded cardinality.
5. Connect timeout is 5 s and the response timeout is 30 s. A slow upstream fails the single request
   instead of holding the connection indefinitely.

### JWT validation happens twice on purpose

The gateway is an OAuth2 resource server: it fetches the JWKS from `AUTH_JWKS_URI` lazily, and
validates signature, expiry, issuer (`sanaa-auth`) and audience (`sanaa-api`) before routing. If the
token is missing, malformed, expired or issued for another audience or issuer, the gateway answers
401 with an RFC 7807 body. auth-service being down does not prevent startup, because the JWKS is
fetched on the first token and cached, not during context refresh.

Every downstream service validates the same token again. The gateway forwards the `Authorization`
header unchanged. Duplicate work is the point: the gateway is an optimisation and a policy point,
not a trust boundary. A service that only checked "the gateway let it through" would trust a header
that any process on the Compose network could send, and a gateway bug or a direct port exposure
would become a full authentication bypass. Validation is cheap (local signature check against a
cached JWKS), so the cost of doing it twice is a few microseconds per request.

The gateway also strips every inbound header whose name starts with `X-Tenant` or `X-User` on all
requests, public or not. A downstream service therefore cannot accidentally read an identity
header, and the only source of tenant identity remains the validated `tenant_id` claim.

### Rate limiting: three policies, one Redis token bucket each

Redis `RequestRateLimiter` (token bucket) with keys that are never empty:

| Policy | Routes | Key | Replenish | Burst |
|---|---|---|---|---|
| A | `POST /api/auth/login`, `/register`, `/refresh` | client IP | 2/s | 10 |
| B | every other authenticated `/api/**` route group | `tenant_id:sub` | 20/s | 40 |
| C | `/api/stream/media/**` | client IP + lesson id | 50/s | 100 |

1. Policy A limits credential stuffing and password guessing per source address. The key is
   `ip:<address>`.
2. Policy B is the normal per-user budget. The key is `user:<tenant_id>:<sub>`, both taken from the
   validated token, never from a header. Redis' default key also contains the route id, so a user
   has one bucket per route group (auth, course, payment) rather than one shared bucket. That is
   intentional: a burst of course browsing cannot exhaust the payment budget. The effective limit
   is therefore 20/s per user per group.
3. Policy C is media delivery, which is the highest-volume path. The key is
   `media:<ip>:<lessonId>`, so seeking through one lesson cannot consume the budget of another and
   one hot lesson cannot consume the whole IP budget.
4. The client IP is resolved with `XForwardedRemoteAddressResolver.maxTrustedIndex(1)`: exactly one
   trusted hop (nginx), which overwrites `X-Forwarded-For` with `$remote_addr`. Trusting one hop
   means a client-supplied header cannot choose the bucket when nginx is the only entry point. If the
   gateway port is reachable directly, the header can be forged, which would only move the attacker
   into another bucket, never grant more total capacity than the targeted bucket allows.
5. Keys are never empty. When no address is known the key falls back to a constant `unknown`
   bucket (fail closed: shared capacity, not unlimited). Unauthenticated requests that reach policy
   B fall back to the IP key.
6. A rejected request returns 429 with `Retry-After: 1`. The limiter completes the response itself,
   so the body is empty by design: the status and header are the machine-readable contract, and a
   body would have to be written from a filter that no longer owns the response. Clients must not
   parse a 429 body.

### Redis is fail-open

The Redis rate limiter logs the failure (`Error calling rate limiter lua`) and allows the request
when Redis is unreachable. No custom fail-open code is added; this is the gateway's built-in
behaviour, verified by a test that stops Redis.

The trade-off is explicit: while Redis is down, request throttling is disabled and every caller is
treated as under its budget. Availability of login, payments and media is considered more important
than temporary throttling for this platform. The gateway still validates JWTs, so an outage does
not become an authentication bypass, and the upstream services keep their own authorization rules.
A production deployment that must not lose throttling would need a local fallback limiter or a
fail-closed switch; that is out of scope for v1.0.0 and is recorded as a known limitation.

### Host versus tenant check

When the `Host` header carries a subdomain of `GATEWAY_BASE_DOMAIN` (default `localhost`, port
ignored), the subdomain must equal the token's `tenant_slug`; otherwise the request is rejected with
403. A user signed in to tenant A must not keep using tenant B's hostname, which matters because the
SPA uses the hostname to choose the tenant for display and for links.

Hosts without a subdomain skip the check: `localhost`, the base domain itself, and IP literals
(IPv4, IPv6 and bracketed forms). Public and unauthenticated requests also skip it, since there is
no token to compare.

Limits of the check:

- A client that can choose the `Host` header can skip the comparison by sending an IP literal or an
  unrelated name. The check is defence in depth for browser flows, not an authorization boundary.
- Authorization still comes from the token claims. The check never widens access; it only removes
  one class of confusing cross-tenant UI state.
- The proxy must preserve the browser-visible hostname. nginx therefore needs
  `proxy_set_header Host $host;`; with `$proxy_host` or the upstream address, every request would
  skip the check.
- Nested names such as `acme.eu.localhost` are compared as a whole and cannot equal a single-label
  slug, so they fail closed.

### No Eureka and no Config Server

The stack has one instance of each service, started by one Compose file on a private network where
DNS names are stable. Eureka would add a registry container, client heartbeats and a failure mode
(stale instances) for no benefit. Config Server would add a second repository, an encryption story
and another bootstrap step, while the twelve-factor alternative (environment variables in `.env`,
`application.yml` for non-secret defaults) already covers the same need and is easier to run from
an IDE. Spring Cloud Gateway therefore routes to fixed URIs from environment variables. If the
deployment ever needs autoscaling or per-environment dynamic routing, service discovery or a
configuration service can be added behind the same gateway contract without changing the client.

### Error contract

The gateway's own rejections (401, 403) are RFC 7807 `ProblemDetail` JSON written by a shared
writer. The resource-server defaults are overridden so that requests carrying an invalid token get
the same body instead of an empty 401. `WWW-Authenticate` keeps the RFC 6750 error code
(`error="invalid_token"`) without the description, which can contain decoder internals. Stack
traces are never returned.

## Consequences

- The gateway and every service must fetch the JWKS. Key rotation requires publishing the new key
  before signing with it; already cached keys keep working for the cache lifetime.
- A 429 has no body. Any client that wants a message must derive it from the status.
- Rate limits are per user per route group, not global per user. Tuning one group does not affect
  the others, and the three groups must be reasoned about separately.
- While Redis is down, throttling is off. Monitoring must alert on the `Error calling rate limiter
  lua` log line rather than relying on the limiter itself.
- The Host check requires the proxy to forward the original `Host`. A deployment that rewrites it
  silently disables the check for browser traffic.
- Gateway tests need Docker for Redis, real RSA keys and stub upstreams; they do not need
  auth-service.
