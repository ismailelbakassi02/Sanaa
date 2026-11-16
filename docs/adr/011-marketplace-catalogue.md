# ADR 011: marketplace catalogue, studio API and cross-tenant reads

- Status: Accepted
- Date: 2026-11-15

## Context

v2 turns course-service from a per-tenant academy into the catalogue of one marketplace. A course
page must show a publisher and an instructor that belong to another tenant, search must span every
publisher, and a learner with no tenant must read and write across publishers. The v1 model
(tenant-filtered JPA reads only, no categories, no tags, no reviews, no views) cannot express that.

This ADR records how the catalogue, the studio API and the learner paths are built, and where the
tenant boundary is crossed on purpose.

## Decisions

### 1. Publisher and instructor data are separate global snapshot tables

`courses` has no publisher or instructor columns. `publisher_snapshots` (PK `tenant_id`) and
`instructor_snapshots` (PK `user_id`) hold the cross-tenant display data. They are deliberately not
`@TenantId`: the public catalogue joins them for every publisher in one query.

WHY not a column on `courses`: a publisher rename would have to update an unbounded number of course
rows, and a suspended publisher must disappear from the catalogue atomically. One row per publisher
is the smallest unit that both requirements need.

Ordering uses the event time (`source_occurred_at`), not the receive time. A redelivered older event
is ignored, so a snapshot can never regress to a stale name or an old ACTIVE status.

A studio can create its first course before the publisher event has been consumed. The studio writes
a placeholder with `source_occurred_at = 1970-01-01` using a native `INSERT IGNORE`, so two concurrent
first creations cannot collide and a failure can never roll back the course. The first real event
always wins because its time is later than the placeholder's.

### 2. Public and learner reads are native SQL in `crosstenant`

`CatalogueReadRepository` is the only native-SQL reader. It returns internal row records, never
entities. It applies the visibility rule itself: `status = 'PUBLISHED'` plus an ACTIVE publisher
snapshot. Public writes are limited to the daily view counter.

WHY: `@TenantId` adds a tenant predicate to every JPA query. A public page for all publishers
cannot use it, and a JPQL query that ignores the tenant is not possible. Isolating the SQL in one
read-only repository (plus an ArchUnit test) is safer than weakening the tenant annotations.

Learner reads filter by the JWT subject in the SQL. No endpoint accepts a user id.

### 3. Cross-tenant writes set the context before the transaction

Free enrollment and review writes resolve the course with the trusted native lookup, take
`tenant_id` from that row, and run `TenantContext.callAs(tenantId, () -> writeBean.method(...))`
before the transaction opens. The write bean is a separate Spring bean: a private `@Transactional`
method would not start a transaction (self invocation bypasses the proxy), and an outer transaction
would open before the tenant is known.

Duplicate guards: `processed_events` for payment events, and `UNIQUE(tenant_id, user_id, course_id)`
for enrollments. A free-enrollment race loses on the unique key; the caller catches
`DataIntegrityViolationException` after the rollback and returns the winner's row instead of a 500.
The review upsert is one row per `(course_id, user_id)` and recomputes the course aggregate in the
same transaction.

### 4. Effective price is computed in one place, with an injectable clock

`Course.effectivePrice(Instant now)` (and the identical expression in SQL for catalogue filters)
returns `discount_price` only while `now` is strictly before `discount_ends_at`. At the exact end
instant the list price is back. A fixed `Clock` bean in tests proves the boundary without sleeping.
Payment-service copies the price at purchase time; no coupon codes exist.

### 5. Search and trending

Search uses `MATCH(title, summary) AGAINST (...)` with `utf8mb4_unicode_ci`, so it is accent and case
insensitive ("etalonnage" finds "Étalonnage et colorimétrie"). The exact tag filter is an `EXISTS`
on `course_tags` with a lower-case tag. Page size is capped at 24.

Trending is `enrollments of the last 30 days DESC, views of the last 7 days DESC, rating DESC, id
DESC`. The two windows are a deliberate tie-breaker: a new course with a view spike can rank without
an old best-seller dominating forever.

Views are one row per course and day, incremented with `INSERT ... ON DUPLICATE KEY UPDATE`. The
increment is outside any transaction and its failure is logged at debug: a counter must never block
a course page.

### 6. Publish rule and read-only statuses

Publishing needs a category, a summary, at least one skill and (when
`sanaa.course.publish.require-ready-video` is true) a lesson with a READY video. The property exists
because the video pipeline is a separate iteration and the local demo has no videos; tests prove
both values. A course with enrollments is archived, never deleted. ARCHIVED and SUSPENDED are
read-only for the publisher; SUSPENDED is only set by platform moderation.

### 7. The studio is tenant-private, the catalogue is not

Studio writes load the course through the tenant-filtered repository, then apply ownership: a
TENANT_ADMIN may edit every course of the tenant, an INSTRUCTOR only their own. Another instructor's
course is a 404 in the studio, so ids do not leak. Positions are server-assigned and contiguous; a
reorder frees every slot with a temporary offset before writing final positions, because the unique
index `(course_id, position)` would otherwise reject a swap.

The old `/api/courses`, `/api/modules`, `/api/lessons` and `/api/enrollments/me` endpoints are gone.
The gateway routes `/api/studio/**` (except payments, settings and complaints) to course-service,
and `/api/courses/**` now carries only the learner write surface and streaming.

## Consequences

- Every course appears in the public catalogue only through an ACTIVE publisher snapshot. A missing
  snapshot hides the course; the studio placeholder and the consumer are therefore both required.
- Native SQL is unfiltered. A missing `status = 'PUBLISHED'` in a new public query is a security
  bug; the public tests and the ArchUnit audience rule exist for that.
- The snapshot tables can be briefly stale after a publisher rename. Events are at-least-once and
  ordered per publisher; the studio never displays a stale publisher name because it does not use
  the snapshots for its own data.
- Reviews update the course row, which bumps its `@Version`. A publisher editing the course at that
  exact moment receives a 409 and retries with the fresh version.
- Admin moderation endpoints (hide a review, suspend a course) are not part of this iteration and
  remain unimplemented in course-service.
