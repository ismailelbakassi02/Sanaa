
PROJECT: SANAA, multi-tenant e-learning SaaS. Portfolio and learning project, target v1.0.0 in 7 days.

Read before any work: `docs/architecture.md` (system design, source of truth) and `docs/Design.md` (UI design system, Arco Design).

## Stack

Backend: Java 27, Spring Boot 4.1.2, Spring Cloud 2025.1.4 (BOM; never hardcode versions managed by a BOM), Spring Security 7, Hibernate 7, MySQL 8.4, Flyway, Kafka, Redis, Testcontainers 2.x.
Frontend: React, TypeScript, Vite, Arco Design (`@arco-design/web-react`), TanStack Query, Zustand.

Boot 4 notes:
- Starters are modular. Use the exact artifact names from start.spring.io or the Boot 4.1 reference docs, not names from Boot 3 tutorials.
- Jackson 3 is the default (`tools.jackson.*` packages). Use `JsonMapper`, not `com.fasterxml` `ObjectMapper`. Jackson annotations keep their old package.
- Spring Security 7 uses the lambda DSL only.
- Testcontainers 2.x has renamed modules and packages.
- If any API differs from your memory, check the official docs for that exact version. Do not guess.
- After the first successful build, write the resolved Hibernate, Kafka client, Testcontainers, Jackson and Arco versions into the Infrastructure section of `docs/architecture.md` (from `mvn dependency:tree` and `npm ls`).

## Hard rules

1. Scope is fixed by `docs/architecture.md`: gateway, auth, course, payment, notification, common, client. Do not add services, libraries or features that are not requested. Ask instead.
2. Tenant id is read ONLY from the validated JWT (or from the Kafka event envelope in consumers). Never from request headers, query params or bodies (except `tenantSlug` on login/register, resolved server-side).
3. Tenant-owned entities use Hibernate `@TenantId`. No native SQL on tenant-owned tables. `TenantContext` must be set BEFORE the transaction/session begins. The resolver is fail-closed (nil UUID when unset). Clear the context in `finally`.
4. Every schema change is a Flyway migration (`V<n>__name.sql`). `ddl-auto=validate`. Never edit an applied migration. `spring.jpa.open-in-view=false`.
5. Layering: controller (DTOs, validation) -> service (transactions) -> repository. Never expose entities in API responses. Use Java records for DTOs and Bean Validation.
6. Errors: RFC 7807 `ProblemDetail` via a `@RestControllerAdvice`. Never leak stack traces.
7. Secrets only from environment variables. Never commit secrets, keys or `.env`.
8. Every feature needs tests. Integration tests use Testcontainers (MySQL, Kafka, Redis), not H2.
9. Logging via SLF4J, no `System.out`, never log tokens, passwords or receipt contents.
10. Keep code simple and comment non-obvious design decisions (explain WHY).
11. Before finishing: run the build and tests, list what you changed, list anything you did not complete or are unsure about. Do not claim something works unless you ran it.
12. If a library API differs from your memory (Spring Cloud 2025.1, Hibernate 7, Spring Kafka, Spring Security 7), check the official reference docs for that version instead of guessing.
13. UI follows `docs/Design.md`: use Arco components and tokens before custom CSS, no hardcoded colors that exist as tokens, dark mode via the `arco-theme` attribute. Do not add Tailwind or a second UI framework. Verify Arco works with the chosen React version; if React 19 does not work cleanly, use React 18 and record it in `docs/adr/003-frontend.md`.
14. Line endings: all `*.sh` files and the MySQL init script use LF. Keep `.gitattributes` enforcing this (the development machine is Windows).
15. Documentation language is English, no emojis anywhere in code, comments or docs.