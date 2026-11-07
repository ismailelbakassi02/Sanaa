# ADR 001: Tenant isolation with a shared database and Hibernate `@TenantId`

- Status: Accepted
- Date: 2026-11-07

## Context

SANAA is a multi-tenant e-learning SaaS. Every tenant owns courses, lessons, enrollments,
payments and users, while a small amount of platform data (tenants themselves) is global. The
system must make cross-tenant reads and writes hard by default, without operating one database per
tenant in the first version.

Each service already has its own database (`auth_db`, `course_db`, `payment_db`,
`notification_db`). This ADR concerns isolation between tenants inside one service database, not
isolation between services.

## Decision

Use one shared database per service with a tenant discriminator column on every tenant-owned
table, and let Hibernate 7's `@TenantId` apply the discriminator automatically.

1. Tenant-owned entities carry a field annotated with `org.hibernate.annotations.TenantId`:

   ```java
   @TenantId
   @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
   private String tenantId;
   ```

2. The current tenant lives in `TenantContext`, a `ThreadLocal` owned by the `common` module. It
   is populated only from a validated JWT claim (`tenant_id`) by `JwtTenantFilter`, or from the
   validated event envelope in Kafka consumers. It is never read from a request header, query
   parameter or request body. The only unauthenticated tenant input is `tenantSlug` on login and
   registration, which auth-service resolves against the global `tenants` table.

3. `TenantIdentifierResolver` supplies the tenant to Hibernate and is fail-closed: when no tenant
   is set it returns the nil UUID `00000000-0000-0000-0000-000000000000`. Every tenant-owned table
   has `CONSTRAINT chk_<table>_tenant_not_nil CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000')`.

4. `TenantContext` is set before the transaction or Hibernate session opens and cleared in a
   `finally` block. Unauthenticated flows use a non-transactional facade that resolves the tenant
   first and then calls a separate `@Transactional` bean inside `TenantContext.callAs(...)`.

5. Tenant-owned tables have no native SQL in application code. The only permitted native query is
   the outbox poller on `outbox_events`, which is intentionally not tenant-scoped.

6. Unique indexes on tenant-owned tables always include `tenant_id` (for example
   `UNIQUE (tenant_id, email)`), so uniqueness is per tenant and the index can serve tenant-leading
   lookups.

## Why not schema per tenant or database per tenant

| Option | Advantages | Costs in this system |
|---|---|---|
| Shared database, discriminator (chosen) | One connection pool and one migration run per service; simple local development; cheap to add tenants; cross-tenant platform queries (tenant list, admin) stay simple | Application must never bypass Hibernate filtering; noisy-neighbor effects; all tenant data sits behind one credential |
| Schema per tenant | Stronger separation inside one server; per-schema backup is possible | Every service needs dynamic schema routing; Flyway must run once per tenant; connection/session handling becomes custom; tenant count multiplies migration work; platform tables need a special case |
| Database per tenant | Strongest isolation; independent backup, restore and failure domain; per-tenant scaling | Highest operational cost at the current scale; every service needs tenant-aware routing and credentials; onboarding a tenant means provisioning and migrating every service database; local development and tests become heavy |

The project targets a small number of tenants in its first version and values a short, testable
implementation. Shared database with a discriminator gives the best isolation per unit of
operational cost, provided the rules above are enforced. The cost is accepted and documented
below rather than hidden.

## Observed behaviour on Hibernate 7.4.13.Final

The behaviour below is not assumed from documentation. It is verified by
`common/src/test/java/com/sanaa/common/tenant/TenantIdIntegrationTest` against MySQL 8.4 in
Testcontainers. The test creates its own `EntityManager` after setting the tenant, because
production also opens the session after the tenant is known.

Observed:

- `EntityManager.find(Entity.class, id)` is filtered by tenant. A row inserted under tenant A is
  returned under tenant A and is `null` under tenant B.
- JPQL queries (`select e from Entity e`) are filtered by tenant. Tenant A sees only its rows;
  tenant B sees none.
- JPQL bulk updates are filtered by tenant. A bulk update issued under tenant B for a row owned by
  tenant A updates zero rows, and the tenant A row keeps its original value.
- With no tenant set, reads return no rows because the resolver supplies the nil UUID.
- With no tenant set, an insert fails the MySQL `chk_tenant_not_nil` CHECK constraint. Hibernate
  populates the discriminator with the nil UUID, and the database rejects it. Neither a read nor a
  write can silently fall back to another tenant.
- Removing `@TenantId` from the test entity makes the integration test fail: Hibernate no longer
  populates `tenant_id`, and the insert is rejected earlier by Hibernate nullability
  (`PropertyValueException`). This proves the test genuinely depends on the annotation rather than
  passing for an unrelated reason.

Not tested in this version:

- JPQL bulk delete. Not tested; no production code performs tenant-scoped bulk deletes yet.
- Native SQL against tenant-owned tables. Not tested and forbidden by the architecture.
- Criteria API queries, entity graphs and association fetching across tenants. Not tested.
- Second-level cache isolation. Not tested; no second-level cache is enabled.
- Propagation of `TenantContext` to application-managed threads, `@Async` executors, schedulers
  and Kafka consumers. The consumers will set the context explicitly from the event envelope; this
  is not tested yet.

## Known limits

1. **Native SQL bypasses filtering.** `@TenantId` only affects Hibernate-generated SQL. A native
   query, a JDBC template or an external client can read or write across tenants. The rule is
   therefore absolute: no native SQL on tenant-owned tables. Code review and tests must enforce
   it. The outbox poller is the documented exception because that table is not tenant-owned.
2. **Unique indexes must include `tenant_id`.** A global `UNIQUE (email)` would prevent the same
   email from existing in two tenants. Every uniqueness rule that is meant to be tenant-local must
   include the discriminator. The database, not only the application, is the final duplicate
   guard.
3. **Noisy neighbor.** Tenants share a connection pool, buffer pool, disk and CPU. One tenant's
   heavy import or report can slow the others. Rate limiting exists at the gateway, but database
   load is not partitioned. This is accepted at the current scale.
4. **Context timing.** `TenantContext` must be set before the transaction or Hibernate session
   starts. Setting it inside a method that is already transactional is too late: the session has
   already resolved its tenant. The non-transactional facade plus `@Transactional` service split
   exists for this reason, and `JwtTenantFilter` runs before controllers for authenticated
   requests.
5. **ThreadLocal leakage.** A pooled request thread or executor thread can keep a stale value.
   `TenantContext.callAs` restores the previous value in a `finally` block, and
   `JwtTenantFilter` clears the context at the start and end of each request. Code that hands work
   to another thread must capture and restore the tenant explicitly.
6. **Caches and search indexes are outside the filter.** Any cache key, Redis key, search document
   or file path that stores tenant data must include the tenant id. Hibernate filtering does not
   help there.
7. **Migrations are global.** Flyway runs once for all tenants. A failed migration affects every
   tenant in that service database, and there is no per-tenant schema version.
8. **Super admin isolation is a policy, not a database property.** `SUPER_ADMIN` manages tenants
   and has no access to tenant content. That is enforced by authorization rules and by the absence
   of a tenant claim for platform operations, not by physical separation.

## When to switch to database per tenant

Database per tenant becomes the better choice when one or more of these is true:

- A contract or regulation requires physical separation, per-tenant encryption keys, or
  per-tenant backup and restore.
- A single tenant's load regularly affects the SLO of other tenants, and rate limiting or query
  tuning no longer contain it.
- Tenant data volume or retention requirements make a shared buffer pool and backup window
  impractical.
- The platform needs to move a tenant to a different region or database engine without moving all
  tenants.
- The operational cost of per-tenant provisioning becomes lower than the cost of shared-database
  safeguards (for example, a mature internal provisioning platform).

Migration path: keep the `tenant_id` column and the application-level rules, add a routing
resolver that maps a tenant to a datasource, move one tenant's rows at a time, and keep the
`tenants` table global. Because all queries are already tenant-scoped by Hibernate and all
uniqueness is tenant-leading, the data model does not need to change; only connection routing and
migration orchestration do.

## Consequences

- Every tenant-owned entity must carry `@TenantId` and every tenant-owned table must have the
  discriminator, the nil CHECK and a tenant-leading index. A missing annotation is a security bug,
  not a style issue.
- Integration tests must use a real MySQL instance (Testcontainers). H2 cannot reproduce the
  MySQL CHECK constraints reliably.
- The isolation guarantee is only as strong as the rule that application code goes through
  Hibernate. This trade-off is accepted for v1.0.0 and revisited when the triggers above are met.
