# ADR 006: course-service data model, ownership and visibility

- Status: Accepted
- Date: 2026-11-09

## Context

course-service owns the tenant content of SANAA: courses, their modules and lessons, and the
read model of enrollments. It is a servlet MVC service on port 8082 with its own `course_db`
schema. Every request is authenticated by the service itself against the auth-service JWKS; the
gateway is an additional early rejection point, not the only one.

This iteration covers course authoring, publishing, the catalog read paths and
`GET /api/enrollments/me`. Media upload, playback tokens and enrollment creation are intentionally
out of scope.

## Decisions

### 1. Tenant isolation uses Hibernate `@TenantId` only

`courses`, `modules`, `lessons` and `enrollments` carry `@TenantId` and the schema has the nil-UUID
CHECK plus a tenant-leading index on each table. `processed_events` is not tenant-owned: the future
consumer records an event id once for all tenants.

`JwtTenantFilter` copies the validated `tenant_id` claim into `TenantContext` after bearer
authentication and clears it in a `finally` block. The Hibernate resolver is fail-closed: with no
context it supplies the nil UUID, so reads match nothing and inserts violate the CHECK constraint.
No repository method accepts a tenant id and no request value can set one.

Consequence: a JWT with an unknown tenant id, or without a `tenant_id` claim, sees an empty catalog
and receives 404 for every course id. This is tested against MySQL.

### 2. `course_id` is denormalized on lessons

A lesson row stores both `module_id` and `course_id`. The module already belongs to a course, so
`course_id` is redundant in a strict relational sense.

WHY: entitlement checks, enrollment lookups and the future streaming authorization need the course
for a given lesson id. Keeping `course_id` on the lesson makes that a single indexed lookup
(`tenant_id`, `course_id`), without joining modules on every playback or enrollment check. The
streaming endpoints will be the hottest read path and the join would buy nothing.

The value is always derived from the module inside course-service, never accepted from the client.
A lesson is created by module id, and the service reads the module's `course_id` in the same
transaction. The database foreign keys keep both references valid; the application keeps them
consistent.

Alternative rejected: a JPA association from `Lesson` to `Module` and from `Module` to `Course`.
It would hide the tenant-filtered queries behind lazy loading, make the read path less explicit and
still require joins. The service uses plain id columns and tenant-filtered repository queries.

### 3. Positions are assigned by the server

The client sends only a title (and lesson duration/preview). It never sends a module or lesson
position. The service computes `max(position) + 1` inside the transaction.

WHY: a client-controlled ordering would let one request overwrite another's ordering, and would
make concurrent authoring depend on whatever the browser last saw. The server-assigned sequence
gives deterministic 1, 2, 3 ordering for the normal case.

The database is the final guard: `UNIQUE(course_id, position)` on modules and
`UNIQUE(module_id, position)` on lessons. Those two indexes do not repeat `tenant_id` because a
course id or module id is already globally unique; the separate tenant-leading indexes on both
tables keep tenant-scoped lookups fast. Two concurrent creations can both read the same maximum.
One insert wins; the other hits the unique index. The API maps that `DataIntegrityViolationException`
to 409 instead of 500, so the client can retry. There is no transparent retry loop: retrying inside
the same failed transaction would not work, and an explicit 409 is honest about the conflict.

### 4. Visibility and ownership are different rules

Visibility decides whether a caller may read a course. Ownership decides whether a caller may
change it. The rules are deliberately not the same.

| Role | Can read | Can create | Can edit, publish, archive, add content |
|---|---|---|---|
| STUDENT | PUBLISHED courses of the tenant | no | no |
| INSTRUCTOR | own courses of any status, plus other instructors' PUBLISHED courses | yes, as themselves | own courses only |
| TENANT_ADMIN | every course of the tenant | yes | every course of the tenant |
| SUPER_ADMIN | no tenant content | no | no |

When a course exists but is not visible, the API returns 404, not 403, so ids and titles do not
leak. When a course is visible but the caller is a non-owner instructor, the API returns 403,
which is accurate: the caller knows the course exists. A tenant administrator bypasses the owner
check inside the tenant only.

Lists follow the same rules. A student sees PUBLISHED only; an instructor sees
`own OR PUBLISHED`; a tenant administrator sees the tenant. The query is always tenant-filtered by
Hibernate, and sorting is fixed to `createdAt DESC` with `id DESC` as a stable tie-breaker. The
client cannot choose a sort. Page size is capped at 50.

### 5. SUPER_ADMIN has no access to tenant content

The platform role manages tenants, not their courses. Giving SUPER_ADMIN content access would
create a second, silent tenant bypass next to the tenant administrator role, and would conflict
with the architecture rule that `SUPER_ADMIN` manages tenants only. The exclusion is enforced at
method security on every course, module, lesson and enrollment handler, so it returns 403 before a
service method runs. It is not a repository-level filter that could be forgotten in one endpoint.

### 6. Enrollments are created by events, not by this API

`POST /api/enrollments` does not exist. When a payment is approved, payment-service publishes
`PAYMENT_APPROVED`; course-service will consume it, set `TenantContext` from the validated event
envelope, insert the event id into `processed_events` and create one ACTIVE enrollment in the same
transaction. An existing enrollment counts as success.

WHY: payment approval is the source of truth for paid access. A public enrollment endpoint would
let a student enroll without paying, or would duplicate the payment state machine inside
course-service. Eventual consistency is accepted: a student may wait a moment after approval.

The event path is at-least-once. `processed_events` provides consumer idempotency, and
`UNIQUE(tenant_id, user_id, course_id)` is the final duplicate guard. This iteration only exposes
`GET /api/enrollments/me`, which returns ACTIVE enrollments with a course summary.

### 7. Course edits use optimistic locking

`courses` has a `@Version` column. `PUT /api/courses/{id}` requires the version the client last
read. A mismatch returns 409 before any field changes, so two editors cannot silently overwrite
each other. Publish and archive change only the status and do not require a version; they still
apply the ownership rules.

Status lifecycle: a course starts DRAFT. Publishing requires at least one lesson and returns 422
otherwise. ARCHIVED is read-only: update, module creation, lesson creation and publishing return
409. Archiving is idempotent.

### 8. Errors, metrics and media

Errors are RFC 7807 `ProblemDetail` from one `@RestControllerAdvice`, including validation errors,
404, 403, 409, 422 and unexpected failures. The response never contains a stack trace, and null
fields are omitted. Security failures before MVC use the same shape.

Prometheus is exposed only on the management port (`MANAGEMENT_SERVER_PORT`, default 9090) with the
tag `application=course-service`. The main port does not expose Actuator. Tenant ids are never
metric tags or metric names.

Media is not implemented. Lessons persist `media_status = NONE`, and no endpoint returns or accepts
media fields.

## Consequences

- Every tenant-owned entity must keep `@TenantId`, and every tenant-owned table must keep the nil
  CHECK and a tenant-leading index. Removing the annotation is a security bug, and the isolation
  tests fail if it is removed.
- Concurrent authoring may return 409 on position collisions. The UI must retry or refetch rather
  than assume every creation succeeds.
- Denormalized `course_id` on lessons requires discipline: only the service may derive it, and a
  future module move (not implemented) must update lessons in the same transaction.
- The event consumer is not implemented yet. Until it is, enrollments exist only through direct
  database fixtures in tests.
- SUPER_ADMIN cannot support or inspect tenant content through this service. Any future support
  workflow needs a separate, audited mechanism, not a quiet role exception.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Client-supplied positions | Allows overwriting other authors' ordering and produces duplicate positions |
| Transparent retry inside the same transaction | The failed transaction is already marked rollback-only; a bounded retry needs a new transaction per attempt and hides a real conflict |
| Return 403 for invisible courses | Leaks the existence of other tenants' or other instructors' drafts |
| Let SUPER_ADMIN read content | Conflicts with the platform/tenant separation and adds a second bypass |
| Synchronous enrollment call from payment-service | Couples payment approval to course-service availability and breaks the event-driven design |
| Media endpoints in this iteration | Media storage and playback tokens are a separate security-sensitive feature |

## Not covered by this ADR

- Media upload, HLS packaging, playback tokens and the streaming filter chain.
- The `PAYMENT_APPROVED` consumer and its retry/DLT behaviour.
- A module or lesson reorder API.
- Course deletion. Content is archived, never deleted.
