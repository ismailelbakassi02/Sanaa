# ADR 008: Payment events, the transactional outbox and the enrollment consumer

- Status: Accepted
- Date: 2026-11-13

## Context

Approving a payment must grant access to a course, and payment-service must not call course-service
synchronously during the review. The two services own separate schemas, so the access can only be
created from an event. The event must not be lost when the broker is unavailable, and it must not be
published twice when a transaction is retried.

The first iteration already wrote an `outbox_events` row in the same transaction as the payment
change but never published it. This iteration adds the publisher, the topics, the consumer that
creates enrollments, and the failure handling on both sides.

## Decisions

### 1. Transactional outbox instead of a dual write

Writing the payment row to MySQL and then calling Kafka is a dual write: the database commit can
succeed while the broker publish fails, or the publish can succeed while the commit rolls back.
Either way the two systems disagree and nothing repairs them.

The service therefore writes the event as a row of `outbox_events` in the same local transaction as
the payment change. A committed payment always has its event, and a rolled-back review never emits
one. The publish itself becomes a separate, retryable step. This is the only table in the system
that is intentionally not tenant-owned: publication is a platform-wide concern, and one query must
see the backlog of every tenant.

### 2. At-least-once delivery forces idempotent consumers

The poller marks a row published after the broker acknowledged it. If the process crashes between
the acknowledgement and the commit, the row stays unpublished and is sent again. Delivery is
therefore at-least-once, never exactly-once, and every consumer must tolerate a redelivery.

course-service uses two layers:

1. `processed_events` records each `eventId` once, across all tenants. The consumer inserts with
   `INSERT IGNORE` and checks the affected row count; zero means "already processed". A plain insert
   would raise a duplicate-key error and mark the surrounding transaction rollback-only, which is why
   the ignore variant is used. Inserting first also means the ledger and the enrollment commit
   together or not at all.
2. The unique key `(tenant_id, user_id, course_id)` on `enrollments` stops a second event for the
   same purchase from creating a second access. When the row already exists, any status counts as
   success.

The first layer protects against redelivery of the same event; the second protects against two
different events (for example a re-approval after a manual operation) describing the same purchase.
The notification consumer uses the same ledger pattern for its own side effect.

### 3. Key equals the payment id, which fixes the partition

The producer sends `key = aggregate_id` (the payment id). Kafka hashes the key, so every event of one
payment lands on the same partition and is consumed in order. `ORDER BY id` in the poller and
"stop the batch on the first failure" preserve that order on the publication side. Events of
different payments may interleave; nothing in the system depends on cross-payment order.

The topic is `sanaa.payments.v1` with three partitions and replication factor 1 (single broker in
development). Both services declare it with `NewTopic`; the declaration is idempotent.

### 4. Retries are bounded and the dead letter topic is the end of the line

course-service uses a `DefaultErrorHandler` with an exponential backoff of three retries
(500 ms, then 1 s, then 2 s). Database hiccups and other transient failures are retried in place.
Malformed JSON, an invalid envelope, an unknown event type and a course that does not exist in the
event's tenant can never succeed on a retry, so they are classified non-retryable and go to
`sanaa.payments.v1.DLT` immediately, with the same key and the same partition.

A poison message therefore costs one failed attempt and one dead-letter copy, not a retry storm and
not a stalled partition. The original exception is attached as a header; the DLT records are read by
an operator, not by another service. If the dead-letter publish itself fails, the error handler
propagates the failure and the offset is not committed, so the record is redelivered instead of
being lost.

The consumer uses `StringDeserializer` and parses with `EventJson` (Jackson 3). Container-managed
deserialization of a typed payload would make a bad record a container-level error, before the
error handler can attach a useful classification.

### 5. The poller stops the batch on the first failure

The poller runs in one transaction, locks up to 50 unpublished rows with
`FOR UPDATE SKIP LOCKED` and sends them in id order. When a send fails it stops instead of skipping
the row: a later event of the same payment must never overtake an earlier one, so the whole tail
waits for the next tick. Rows published before the failure keep their `published_at` value and the
transaction commits them; the failed row and every later row are retried on the next tick, one
second later.

`SKIP LOCKED` is also what makes several pollers safe: a second instance skips the rows locked by
the first one and picks up the next batch, so no row is published twice by concurrency.

### 6. Metrics never carry a tenant

The outbox exposes `sanaa_outbox_unpublished`, `sanaa_outbox_published_total` and
`sanaa_outbox_publish_failures_total`. The consumer exposes
`sanaa_events_processed_total{result="processed|duplicate|ignored|dead_lettered"}`. All carry
`application=payment-service` or `application=course-service`, and none carries a tenant id: the
tenant is data, not an operational dimension, and tenant tags would leak identity into the metrics
backend.

## Known limits

- The poller holds row locks while it waits for the broker, up to the 5 s acknowledgement timeout per
  row and the 50-row batch size. A slow broker delays other pollers on the same rows; it does not
  corrupt anything, but a large backlog drains serially per instance.
- `outbox_events` rows are never deleted. The table grows forever and its `published_at IS NULL`
  index keeps the poller fast, but a retention job is out of scope.
- There is no replay tool for the DLT. Messages are inspected manually and, if they are recoverable,
  republished by hand. `processed_events` would make a replay of a successfully processed event a
  duplicate, which is the safe outcome.
- Kafka is bound to localhost in development (host listener `29092`) and PLAINTEXT, with no
  authentication or TLS. The broker is trusted infrastructure; only that network can produce or
  consume events. A production deployment needs SASL/TLS and a restricted listener.
- The producer uses bounded timeouts (3 s block, 3 s request, 6 s delivery). A deeper broker outage
  fails the tick quickly and retries later, but throughput during the outage is zero and the
  in-memory producer buffer is not a durable queue.
- The consumer group offset is committed per record. A crash after the database commit but before
  the offset commit causes a redelivery, which the ledger absorbs.
