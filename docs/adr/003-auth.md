# ADR 003: Authentication tokens, refresh rotation and cookies

- Status: Accepted
- Date: 2026-11-07

## Context

auth-service issues the credentials every other service trusts. The token API is stateless for
normal requests, but a short-lived access token alone would force users to log in every 15 minutes.
A long-lived bearer token in browser storage would be readable by any script on the page and cannot
be revoked. The platform also needs tenant administrators, platform tenant management and a
repeatable demo dataset.

The frontend calls the API from the same origin (through the gateway) and uses cookies only for the
refresh session. All other endpoints authenticate with an `Authorization: Bearer` header.

## Decision

### Access tokens: RS256 and JWKS

1. auth-service signs access tokens with an RSA private key loaded from a file path supplied by
   `JWT_PRIVATE_KEY_PATH`. The key never enters the jar or the repository.
2. Tokens use RS256 with a stable `kid` derived from the public key thumbprint.
3. Resource servers validate tokens against `/.well-known/jwks.json`, which exposes only public key
   material. They never receive the private key and never call auth-service per request.
4. Access tokens live 15 minutes and carry `sub`, `iss`, `aud`, `jti`, `tenant_id`, `tenant_slug`,
   `role`, `email` and `name`.
5. Any service that trusts the token must validate signature, issuer, audience and expiry. Tenant id
   is read only from the validated `tenant_id` claim, never from a header, query parameter or body.

### Refresh tokens: opaque, hashed, rotated

1. A refresh token is 32 bytes from `SecureRandom`, encoded as base64url without padding (256 bits
   of entropy). It is not a JWT: it carries no claims and is meaningless outside auth-service.
2. The database stores only the SHA-256 hex digest in `refresh_tokens.token_hash`. A database leak
   therefore does not expose a usable token. The raw value exists only in the response cookie.
3. The token has a 7-day TTL (`sanaa.auth.refresh-token-ttl`). Expired, unknown and missing cookies
   all return the same generic 401 so an attacker cannot distinguish the cases.
4. Every successful refresh rotates: the presented row is revoked with a conditional update
   (`UPDATE refresh_tokens SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL`), exactly one
   affected row is required, and a new token row plus a new access token are issued.
5. Reuse detection: presenting a token whose `revoked_at` is already set revokes all refresh tokens
   of that user and returns 401. The same guard covers a concurrent race: one request wins the
   conditional update, the loser sees zero affected rows, revokes every token of the user and
   returns 401.
6. `refresh_tokens` deliberately has no Hibernate `@TenantId`. Refresh and logout receive only the
   cookie, so the row must be found by hash before a tenant is known. The facade sets
   `TenantContext` from the row's `tenant_id` before any tenant-owned transaction opens.
7. On every refresh, auth-service re-checks that the user is `ACTIVE` and the tenant is `ACTIVE`.
   A suspended tenant cannot refresh even if it still holds a valid refresh cookie.

### Cookies

The refresh token is delivered as a cookie named `sanaa_refresh` with:

| Attribute | Value | Reason |
|---|---|---|
| `HttpOnly` | always | JavaScript cannot read the token, so XSS cannot exfiltrate the long-lived credential |
| `SameSite` | `Strict` | a cross-site request never carries the cookie, which blocks CSRF against refresh and logout |
| `Path` | `/api/auth` | the cookie is not sent to course, payment or media endpoints |
| `Secure` | `COOKIE_SECURE`, default `true`; `false` only in the dev profile | production must use TLS; local HTTP development would otherwise drop the cookie |
| `Max-Age` | refresh TTL (7 days) | the browser discards the session when the server would reject it anyway |
| `Domain` | unset | host-only cookie, no sharing across subdomains |

Login sets the cookie and still returns only `{accessToken, expiresIn}` in JSON. Refresh sets a new
cookie and returns the same JSON shape. Logout revokes the presented token if present, clears the
cookie with `Max-Age=0`, and always returns 204.

### CSRF is disabled, and why that is safe here

The API is a stateless bearer-token API. State-changing requests authenticate with an
`Authorization` header that a cross-site form or image cannot set, so classic CSRF does not apply
and Spring Security CSRF protection stays disabled. The two cookie-bearing endpoints are the
exception, and they are protected by `SameSite=Strict`: a request initiated by another origin will
not include `sanaa_refresh`, so an attacker cannot rotate or revoke a victim's session. Responses
to refresh and logout are not readable cross-origin either, and neither endpoint changes data that
an attacker can choose. If the frontend and API ever move to different sites, those endpoints must
move to an explicit CSRF token before `SameSite` is relaxed.

### Suspended tenants and access-token validity

Access tokens are self-contained and validated locally by each resource server against the JWKS.
Suspending a tenant revokes all of its refresh tokens immediately, so no new access token can be
obtained and the session ends within one refresh cycle. Existing access tokens remain
cryptographically valid until they expire, at most 15 minutes. Pushing revocation into every
resource server would require a shared token denylist or an introspection call per request, which
reintroduces the central dependency that JWTs are meant to remove. The 15-minute window is the
accepted trade-off: it bounds the damage while keeping request validation local and fast. The same
reasoning applies to a disabled user.

### Tenant and platform administration

1. `POST /api/auth/admin/users` requires `TENANT_ADMIN`. The tenant comes from the validated JWT.
   The request has no tenant field, and unknown fields are ignored, so a caller cannot create a user
   in another tenant. `SUPER_ADMIN` is intentionally rejected here: platform staff do not manage
   tenant users.
2. `/api/platform/tenants` is limited to `SUPER_ADMIN`. Creating a tenant generates the UUID first,
   sets `TenantContext` to it, and then calls one transactional service that inserts the tenant and
   its first `TENANT_ADMIN`. Both rows commit or roll back together.
3. Slugs must match `^[a-z0-9-]{3,63}$`, must not be reserved (`platform`, `www`, `api`, `admin`,
   `auth`, `app`, `static`, `mail`) and must be unique. Public registration treats reserved slugs
   exactly like unknown tenants: the same 404, with no hint that the label exists.
4. Suspending a tenant revokes all of its refresh tokens. The `platform` tenant cannot be suspended,
   because that would lock out platform administration.
5. The demo dataset (`bhihi`, `acme`) is created only under the `demo` profile. It is idempotent and
   reads the password from `DEMO_PASSWORD`; a missing value fails startup instead of falling back to
   a hardcoded password.

## Consequences

- A stolen refresh token is usable once. Reuse revokes the whole user session, which is visible to
  the legitimate user as a forced login.
- Refresh lookups are global by hash. The table is not tenant-filtered by Hibernate; the facade must
  keep setting the tenant context from the row before tenant-owned work. This is covered by tests.
- Access tokens cannot be revoked before expiry. Suspension and user disablement take full effect
  within 15 minutes, and immediately for refresh.
- The gateway and every resource server must fetch and cache the JWKS. Key rotation requires
  publishing the new key before signing with it.
- Cookie-based refresh requires TLS outside local development. `COOKIE_SECURE` defaults to `true` so
  a misconfigured deployment fails closed rather than sending the token in clear text.
