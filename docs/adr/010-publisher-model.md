# ADR 010: Global identity and the publisher model

- Status: Accepted
- Date: 2026-11-13
- Supersedes: the identity, claims and routing parts of ADR 003 and ADR 004 that this document
  changes.

## Context

V1 treated a tenant as the owner of its users: `users.tenant_id` was mandatory, an email was unique
per tenant, and a learner belonged to one organisation. That model cannot represent the product we
want to ship:

- a learner must be able to follow courses from several publishers with one account;
- a person must be able to register first and open a publisher later without creating a second
  account;
- a publisher can be an organisation with a team, or an individual creator;
- the same person can be an instructor in one organisation and a learner elsewhere;
- the platform team must moderate users and publishers without knowing the admin password of every
  publisher.

"Sanaa Platform" was also modelled as a tenant in V1, which leaked into `tenant_id` claims, the
gateway host check and the admin API. The platform is not a publisher.

## Decision

### 1. Users are global

`users` has no `tenant_id`. An email is unique across the whole platform and is stored lower-cased
(`uk_users_email`). A user has a `platform_role` of `USER` or `SUPER_ADMIN`, a `status` of `ACTIVE`
or `DISABLED`, and a `must_change_password` flag. `SUPER_ADMIN` is a platform role, not a
membership.

A global user may be:

- a learner with no membership;
- a member of exactly one publisher (v1.0.0 rule, enforced by `UNIQUE(user_id)`);
- a `SUPER_ADMIN` with no membership.

### 2. A publisher is a tenant

The existing `tenants` table remains the publisher table. It gains `type` (`INDIVIDUAL` or
`ORGANIZATION`), profile fields (`headline`, `description`, four HTTPS links, `logo_image_id`,
`contact_email`) and keeps `status` (`ACTIVE` or `SUSPENDED`) and `primary_color`.

The old `logo_url` is dropped: v2 stores uploaded images by id, so a publisher cannot point the
public page at an arbitrary URL.

`memberships` is the only link between a user and a publisher. It is tenant-owned
(`@TenantId`), has a role of `TENANT_ADMIN` or `INSTRUCTOR`, a status of `ACTIVE` or `DISABLED`,
and `UNIQUE(user_id)`. A membership is written only inside `TenantContext.callAs(publisherId)`, so
Hibernate stamps the tenant discriminator.

### 3. Claims are derived, never trusted from the request

Tenant id still comes only from the validated JWT (or the Kafka envelope in consumers). On login and
on every refresh, auth-service resolves the current membership and publisher:

| Situation | `role` | Tenant claims |
|---|---|---|
| platform `SUPER_ADMIN` | `SUPER_ADMIN` | absent |
| active membership, publisher `ACTIVE` | `TENANT_ADMIN` or `INSTRUCTOR` | `tenant_id`, `tenant_slug`, `tenant_type` |
| no membership | `STUDENT` | absent |
| publisher `SUSPENDED` | `STUDENT` | absent |
| membership `DISABLED` | `STUDENT` | absent |
| user `DISABLED` | login and refresh fail with 401 | not issued |

`must_change_password` is a boolean claim. It is true for an account created with a temporary
password, and is cleared by `POST /api/auth/password`.

Because claims are re-evaluated on refresh, suspending a publisher or disabling a membership takes
effect without deleting sessions: the next access token has the downgraded role and no tenant
claims. A disabled user cannot log in or refresh.

### 4. Authentication API

- `POST /api/auth/register` creates a global learner. There is no `tenantSlug`; the password is
  10 to 72 bytes (BCrypt limit), and the email is normalized. A duplicate email returns 409 even
  under concurrent requests.
- `POST /api/auth/login` takes email and password only. Unknown email, wrong password and disabled
  account return the same 401 and the service performs a BCrypt comparison on every path, so the
  response time does not reveal whether the account exists.
- `POST /api/auth/refresh` rotates the opaque refresh cookie, re-evaluates the claims and can
  downgrade the session. Reuse of an already rotated token revokes the user's other sessions.
- `POST /api/auth/logout` revokes the presented refresh token and clears the cookie.
- `POST /api/auth/password` requires the current and new password, revokes the other refresh
  tokens, clears `must_change_password`, and returns a fresh access token and refresh cookie.
- While `must_change_password` is true, every auth endpoint except `POST /api/auth/password` and
  `POST /api/auth/logout` returns 403. The rule is enforced in auth-service and again in the
  gateway.

  The servlet rule is not copied per service: `com.sanaa.common.security.MustChangePasswordFilter`
  is registered opt-in in the security chain of auth-service, course-service and payment-service,
  after bearer authentication (and after `JwtTenantFilter`). Each service writes the same five-field
  RFC 7807 body, and a new endpoint cannot forget the rule because the filter runs before the
  controller. The gateway keeps its reactive global filter, because a restricted session must be
  stopped at the edge before it is routed.

Anonymous publisher creation is `POST /api/auth/publishers`. It accepts owner name, email and
password together with the publisher data, and creates the publisher, the owner account, the
membership and the outbox row in one transaction. A logged-in learner can call the same endpoint
without owner fields; the existing account becomes the owner. A user who already has a membership
gets 409. Slugs are validated against reserved words and a unique index, so ten parallel requests
with the same slug produce exactly one 201. The response is `201 {slug, name, type}` and contains no
token or cookie: the owner logs in afterwards.

### 5. Images

Avatars and logos are stored on disk under `sanaa.images.directory` (`IMAGES_DIR`, default
`./data/images`) with a UUID file name, and metadata lives in `images`. The declared content type is
ignored; PNG and JPEG are detected from magic bytes. The limit is 1 MB. A logo requires
`TENANT_ADMIN`; replacing a logo deletes the previous file and row. `GET /api/public/images/{id}`
is public, serves the stored content type with `X-Content-Type-Options: nosniff` and
`Cache-Control: max-age=3600`, and returns 404 for an unknown id.

### 6. Public pages

`GET /api/public/publishers/{slug}` and `GET /api/public/instructors/{userId}` are anonymous.
They never expose emails, contact email or internal identifiers beyond what the page needs.
Unknown or suspended publishers return 404. An instructor page exists only for an active member of
an active publisher.

These reads cross tenant boundaries. They use read-only `crosstenant` entities without
`@TenantId`; they never use native SQL on tenant-owned tables and never accept a tenant id from the
request.

### 7. Team and platform administration

Team endpoints are available to `TENANT_ADMIN` of an `ORGANIZATION` publisher; an `INDIVIDUAL`
publisher gets 409, and `STUDENT`/`INSTRUCTOR` get 403. The tenant always comes from the JWT. The
team list is capped at 50 rows. Adding a member creates a global account with a temporary password,
sets `must_change_password`, and returns 201. The last active `TENANT_ADMIN` cannot be disabled,
including under two parallel requests; the check locks the active admin rows. Disabling a member
revokes their refresh tokens.

Platform administration is `SUPER_ADMIN` only. Users can be listed and read, and their status can
be changed with a reason (5 to 300 characters); an actor cannot disable their own account.
Publishers can be listed and suspended or reactivated with a reason; suspension revokes the
members' refresh tokens and appends a `PUBLISHER_UPSERTED` event. The audit log records every admin
write and every personal-data read, with filters on actor, action, target and date.

`admin_audit_log` is append-only: database triggers reject `UPDATE` and `DELETE`, not only the
service code.

Auditing fails closed. `AuditService.record` inserts the row in the caller's transaction, so the
audit row and the admin write (or the personal-data read it records) commit or roll back together.
If the insert fails, the admin action fails with a 500 `ProblemDetail` and no change is persisted:
an unaudited modification is never accepted. The integration test
`AuthFlowIntegrationTest#aFailedAuditInsertRollsBackTheAdminAction` proves it with a spy that throws
on `record`.

### 8. Outbox and events

Publisher changes write an `outbox_events` row in the same transaction as the change. A poller locks
unpublished rows with `FOR UPDATE SKIP LOCKED`, sends them to `sanaa.publishers.v1` (three
partitions plus a dead-letter topic) with the publisher id as key, and marks each row published. It
stops the batch on the first failure, uses bounded producer timeouts, and never blocks an HTTP
request. The service starts when Kafka is down; rows are published when the broker returns.

Payloads:

- `PUBLISHER_UPSERTED` carries publisher id, slug, name, type, status, contact email and primary
  color;
- `INSTRUCTOR_UPDATED` carries user id, tenant id and display name.

The gauge `sanaa.outbox.unpublished` exposes the backlog. `sanaa.publishers.created` and
`sanaa.users.registered` are counters without a tenant tag, because a tenant tag would create
unbounded metric cardinality.

### 9. Gateway routing and policies

The gateway keeps validating the JWT, sanitizing `X-Tenant*` and `X-User*` headers, and applying
Redis rate limits. The V1 `Host`-versus-tenant check is removed: a publisher is no longer selected
by hostname, and the public pages use slugs.

Routes follow the owning service, including the support service on port 8085:

| Prefix | Upstream |
|---|---|
| `/api/auth/**`, `/api/admin/users/**`, `/api/admin/publishers/**`, `/api/admin/audit/auth` | auth-service |
| `/api/courses/**`, `/api/modules/**`, `/api/lessons/**`, `/api/enrollments/**`, `/api/stream/**`, `/api/learner/**`, `/api/studio/courses/**`, `/api/studio/lessons/**`, `/api/studio/reviews/**`, `/api/studio/stats`, `/api/admin/courses/**`, `/api/admin/reviews/**`, `/api/admin/stats`, `/api/admin/audit/course` | course-service |
| `/api/payments/**`, `/api/studio/payments/**`, `/api/studio/payment-settings/**`, `/api/admin/payments/**`, `/api/admin/audit/payment` | payment-service |
| `/api/complaints/**`, `/api/studio/complaints/**`, `/api/admin/complaints/**`, `/api/admin/audit/support` | support-service (`SUPPORT_SERVICE_URI`, default `http://localhost:8085`) |

Public routes are `POST /api/auth/login|register|refresh|logout`, `POST /api/auth/publishers`,
`GET /api/auth/publishers/slug-available`, `GET /api/public/**`, media playback and the signed card
callback. Policies:

| Policy | Scope | Key | Rate and burst |
|---|---|---|---|
| A | login, register, refresh, slug check | client IP | 2/s, burst 10 |
| D | publisher creation | client IP | burst 5, then 1/min |
| E | `/api/public/**` | client IP | 30/s, burst 60 |
| B | authenticated routes | `sub` | 20/s, burst 40 |
| C | media | client IP and lesson | 50/s, burst 100 |

Policy D is expressed with the built-in Redis limiter as 1 token/s, a capacity of 300 and a request
cost of 60, because Spring Cloud Gateway binds `replenishRate` to an integer: one request per minute
after a burst of five.

Policy B is keyed by `sub` only. The old `tenant_id:sub` key made a learner and a publisher member
share nothing, and a user who changed publisher would get a new bucket; the subject is stable across
both. A restricted session (`must_change_password=true`) can only call `POST /api/auth/password` and
`POST /api/auth/logout`; every other routed path returns 403 with a `ProblemDetail`.

### 10. Migration

`V2__publisher_model.sql` transforms a populated V1 database in place; V1 is never edited. The data
migration:

- turns every tenant user into a global user;
- gives former `TENANT_ADMIN` and `INSTRUCTOR` users a membership in their old tenant;
- leaves former `STUDENT` users as global learners;
- removes the `platform` tenant and makes its admin a global `SUPER_ADMIN`;
- marks `atlas` as `ORGANIZATION` and `acme` as `INDIVIDUAL`;
- makes duplicate emails unique: the lowest user id keeps the address, the later rows become
  `local+<tenantSlug>@domain`, and every rename is written to `admin_audit_log` as
  `MIGRATION_EMAIL_RENAMED`;
- makes `refresh_tokens.tenant_id` nullable, because a learner has no tenant;
- creates the append-only audit triggers.

MySQL 8 enables binary logging by default (`log_bin=ON`). Creating a trigger while binary logging
is on normally requires `SUPER` or `log_bin_trust_function_creators=1`; the SANAA service users are
deliberately not `SUPER`, so the server must trust their trigger creation. Set it globally
(`SET GLOBAL log_bin_trust_function_creators = 1`, which needs `SUPER` or
`SYSTEM_VARIABLES_ADMIN`), or start the server with `--log-bin-trust-function-creators=1`.

Both local paths do the latter:

- Compose: `docker/docker-compose.yml` starts MySQL with
  `command: ["--log-bin-trust-function-creators=1"]`.
- Testcontainers: the tests that run the V2 migration and therefore create the triggers
  (`AuthFlowIntegrationTest`, `OutboxKafkaIntegrationTest`) use
  `.withCommand("--log-bin-trust-function-creators=1")` on the `mysql:8.4` container. Tests that do
  not create triggers do not need the flag.

A production MySQL must set the same option (or grant the migration user `SUPER`/the needed
dynamic privilege) before Flyway applies `V2__publisher_model.sql`; otherwise the migration fails.

## Consequences

- A learner can use one account across publishers, and can open a publisher later without a second
  registration.
- Refresh is the point where membership and publisher status are re-evaluated; access tokens keep
  their claims until expiry. This is deliberate: a 15-minute access token limits the window and
  avoids a database call on every request.
- The client written for V1 selects a tenant before login and sends `tenantSlug`; it does not work
  against this API until the next frontend task. This is expected and tracked.
- `scripts/seed-demo.ps1` still logs in with the V1 `tenantSlug` payload and the removed
  `instructor@acme.local` account. It may fail until the course-service task updates it.
- Native SQL on tenant-owned tables is forbidden outside the outbox, processed-event and
  crosstenant packages. An ArchUnit rule in auth-service enforces it.
- The outbox gives at-least-once delivery. Consumers must be idempotent on `eventId`.
