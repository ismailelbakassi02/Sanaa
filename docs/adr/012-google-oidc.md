# ADR 012: Google OpenID Connect beside password login

- Status: accepted
- Date: 2026-11-16

## Context

SANAA already authenticates global users with an email/password pair, then issues its own RS256
access token and rotating opaque refresh cookie. Google login must be an additional credential for
the same account model, not a second token format trusted by the gateway. A verified Google email
can match an existing password account, but linking by email alone would silently change how that
account can be entered.

## Decision

1. auth-service is an OpenID Connect client using Spring Security 7.1 and the authorization-code
   flow. The browser starts at `/api/auth/oauth2/google`; Google returns to
   `/api/auth/oauth2/callback/google` through the gateway.
2. Spring validates state, nonce, issuer, audience and the signed ID token. SANAA additionally
   requires `email_verified=true`. The provider identity is keyed by Google's stable `sub`; email is
   only a verified contact and account-discovery value.
3. A new verified email creates a global STUDENT with a null password hash and a GOOGLE external
   identity. The authenticated user may define one initial local password later. Normal password
   registration and login are unchanged.
4. An existing password email is not auto-linked. The callback creates a 256-bit opaque ticket,
   stores only its SHA-256 hash for ten minutes and sends the raw value in an HttpOnly, SameSite
   Strict cookie scoped to the link endpoint. The existing password is required before the GOOGLE
   identity is inserted. Tickets are one-use.
5. Google access and ID tokens are never persisted, returned to the SPA, logged or accepted by the
   gateway. After either login method, auth-service issues the same SANAA refresh cookie and the SPA
   obtains the normal access token from `/api/auth/refresh`.
6. The OAuth authorization request uses an HttpOnly SameSite Lax servlet session, scoped to
   `/api/auth/oauth2`, solely for state, nonce and the authorized-client response. Its lifetime is
   five minutes and it is invalidated by both success and failure handlers. The normal API security
   chain remains stateless.
7. The trusted frontend origin comes from `CLIENT_BASE_URL`; no request parameter controls the
   post-login origin. The SPA preserves only a sanitized internal path in session storage.
8. Google is optional. `GOOGLE_OAUTH_ENABLED=false` removes the OAuth security chain while the
   status endpoint lets the SPA hide the button. Client ID and secret come only from environment
   variables.

## Data model

- `external_identities`: one provider subject per user/provider, with the last verified provider
  email; no provider tokens.
- `oauth_link_attempts`: hashed opaque ticket, target user, provider subject/email, expiry and
  consumption time.
- `users.password_hash` is nullable only for an externally created account until it defines its
  first password.

## Consequences

- A password account keeps working after Google is linked, and either method produces identical
  authorization claims.
- A Google-created account can add normal login without a reset-email flow.
- Linking costs one extra password confirmation, intentionally preferring account safety over a
  silent email match.
- Local end-to-end Google testing requires the exact callback URI to be present in the Google Cloud
  Web client; an empty or mismatched URI is rejected by Google before SANAA is called.
