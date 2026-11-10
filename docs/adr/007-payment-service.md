# ADR 007: payment-service submission, receipts and review workflow

- Status: Accepted
- Date: 2026-11-10

## Context

Students pay for a course by bank wire or CashPlus and upload a receipt. A tenant administrator
reviews the receipt and approves or rejects the payment. Only an approval may later grant access,
and that access is created by course-service from a Kafka event, not by a synchronous call.

payment-service is a servlet MVC service on port 8083 with its own `payment_db` schema. It validates
tokens itself against the auth-service JWKS and reads course data over `RestClient`. This iteration
covers submission, receipt storage, listing, download and review, and writes the transactional
outbox. It does not publish to Kafka and does not call course-service to create enrollments.

## Decisions

### 1. The client chooses the course, never the amount

`POST /api/payments` receives `courseId`, `method` and the receipt. The amount, currency and course
title are read from `GET /api/courses/{id}` on course-service, forwarding the student's own bearer
token. The client cannot influence money fields, and course-service applies the same tenant and
visibility rules it uses for every other read, so a payment can only reference a course the student
can actually see.

The course must be `PUBLISHED`. A 404 from course-service, or a non-published course, is a 404 here.
A price of zero is 422 ("Free courses do not need a payment"), because a free course must be taken
without a payment row. A connection failure or timeout (2 s connect, 5 s read) is a 503: the service
refuses to create a payment with guessed data.

The amount, title, email and name are copied onto the payment row as snapshots. Notifications must
not depend on a later course rename or deletion, and the receipt must stay attached to the price the
student actually paid.

### 2. Receipt type is decided by magic bytes

The browser-declared Content-Type and the filename are attacker-controlled. The service reads the
file (at most 5 MB) and accepts only the JPEG (`FF D8 FF`), PNG (`89 50 4E 47 0D 0A 1A 0A`) or PDF
(`%PDF`) signature. A text file renamed to `.pdf` is rejected with 422, and a real PNG sent as
`text/plain` is accepted with its verified type `image/png` stored in the row.

The original filename never touches the filesystem. The file is stored through `ReceiptStorage` as
`<tenantId>/<random UUID>` without an extension. The tenant directory keeps the disk browsable for
an operator, while the random name removes the upload name from every path. The row stores only the
relative location, and the download endpoint resolves it under the configured root and rejects
paths that escape it.

### 3. One open payment per student and course, enforced by the database

A student may submit again after a rejection, but must not hold two open payments for the same
course. The rule is a stored generated column:

```sql
active_key VARCHAR(80) GENERATED ALWAYS AS (
  CASE WHEN status IN ('PENDING_REVIEW', 'APPROVED')
       THEN CONCAT(tenant_id, ':', user_id, ':', course_id)
       ELSE NULL END
) STORED
```

with a unique index. MySQL ignores NULL values, so a rejected payment frees the slot. Because the
guard is in the database, two concurrent submissions produce one 201 and one 409, not two rows and
not a 500. Hibernate maps the column read-only, and the unique violation is translated to 409.

### 4. The outbox row is written in the same transaction

The payment change and its `PAYMENT_SUBMITTED`, `PAYMENT_APPROVED` or `PAYMENT_REJECTED` outbox row
are inserted in one transaction. Either both commit or neither does: an approved payment always has
its event, and a rolled-back review never emits one. The payload is the common `EventEnvelope`
serialized with `EventJson` (Jackson 3), with the tenant id copied as data, topic
`sanaa.payments.v1`, a random event id and the payment id as aggregate id.

`outbox_events` is deliberately not `@TenantId`: the future poller must select unpublished rows
across all tenants with one native query, because publication is a platform concern. This is the
documented exception to the tenant-filter rule, and the table still carries the nil-UUID CHECK.

Approving twice checks the status and relies on the optimistic version, so exactly one transition
succeeds. The review transaction writes one outbox row; the loser gets 409 and writes none.

### 5. INSTRUCTOR and SUPER_ADMIN have no payment access

Instructors create content; they do not handle money or student receipts. A platform super admin
manages tenants, not tenant payments. Both roles are absent from every `@PreAuthorize` list, so they
receive 403 before any handler runs. A student sees only their own payments and receipt; a tenant
admin sees the tenant's payments. A payment of another tenant behaves like a missing one (404)
because Hibernate filters by the tenant from the token.

### 6. Review transitions are explicit and single-step

Only `PENDING_REVIEW` can move to `APPROVED` or `REJECTED`. An approve after a reject, or a second
approve, is 409. A rejection requires a reason between 3 and 500 characters, stored on the row and
included in the event payload for the student notification. The reviewer id comes from the token
subject, never from the request. Kafka publication and the course-service enrollment call stay out
of this service: the approval only records the decision and the event.

## Consequences and known limits

- Receipts live on the local disk of the service (`RECEIPTS_DIR`, default `./data/receipts`). There
  is no replication, lifecycle policy or backup beyond whatever the host provides, and a second
  instance without the same volume would not see the files. Moving to object storage only requires
  another `ReceiptStorage` implementation.
- There is no virus scanning. Files are not executed or served inline (always `attachment` with
  `X-Content-Type-Options: nosniff`), but a malicious PDF is still stored and can be downloaded by
  an administrator.
- `Content-Disposition` is a fixed `attachment` without a filename, so the browser picks the name.
  A nicer name would require deriving an extension from the stored content type.
- The generated column encodes the tuple as text. Changing the tuple (for example adding a course
  session) requires a migration of both the expression and the index.
- The outbox poller and the Kafka producer are not implemented yet; rows stay with
  `published_at IS NULL` until that step. Delivery will therefore be at-least-once, and consumers
  must stay idempotent.
- Payment amount and currency are snapshots. A later course price change does not update an existing
  payment, which is intentional but means the catalog and the payment history can disagree.
- course-service is called synchronously during submission. While it is down, students cannot submit
  payments (503); the service does not queue submissions.
- The management port is separate and not exposed through the gateway, but local development runs it
  on plain HTTP with no network isolation beyond the host firewall.
