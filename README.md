# SANAA

SANAA is a multi-tenant e-learning SaaS built as a portfolio and learning project. Tenants share
one MySQL schema and are isolated with a Hibernate tenant discriminator; users sign in with
short-lived RS256 access tokens; every `/api` call goes through a Spring Cloud Gateway that
validates the token, applies Redis rate limits and routes to the backend services. The target scope
is courses, enrollment, payments and token-gated media delivery. The table below shows what is
actually implemented today.

## Status

| Module | Status | Scope |
|---|---|---|
| `common` | Done | Tenant context and `@TenantId`, JWT claims, role model, JSON event envelope |
| `auth-service` | Done | Register, login, refresh rotation, logout, JWKS, branding, tenant administration, platform bootstrap, demo seed |
| `gateway` | Done | JWT validation, routing, Redis rate limits, header sanitization, Host-vs-tenant check |
| `course-service` | Planned | Courses, modules, lessons, enrollment, protected media |
| `payment-service` | Planned | Payments, receipts, review workflow |
| `notification-service` | Planned | Kafka consumers and email delivery |
| `client` | Planned | React single-page application (not scaffolded) |

Only the modules marked Done are implemented and covered by automated tests. The planned modules
currently contain a Spring Boot entry point and configuration only.

## Tech stack

Backend versions resolved from the build and recorded in the Infrastructure section of
`docs/architecture.md`:

- Java 27
- Spring Boot 4.1.2 (Spring Framework 7, Spring Security 7)
- Spring Cloud 2025.1.4
- Hibernate ORM 7.4.13.Final
- Jackson 3.1.7
- MySQL 8.4 with Flyway migrations
- Redis 7
- Kafka clients 4.2.2 (KRaft mode)
- Testcontainers 2.0.5

Planned frontend: React 19, TypeScript, Vite, Arco Design, TanStack Query and Zustand. No client
version is pinned because the client has not been scaffolded.

See [docs/architecture.md](docs/architecture.md) for the system design, tenancy model, API contract
and infrastructure. [docs/Design.md](docs/Design.md) is the UI design reference.

## Run locally

Prerequisites:

- JDK 27, with `JAVA_HOME` pointing at that installation.
- Maven 3.9 or newer.
- Docker with Compose running.

Run the commands from the repository root. The shell examples use a POSIX shell; on Windows use
WSL or Git Bash.

Create the environment file, generate the JWT keys, and start the infrastructure containers
(MySQL, Redis, Kafka, Mailpit, Prometheus):

```sh
cp .env.example .env
# Edit .env: set the database passwords and DEMO_PASSWORD.
sh scripts/gen-jwt-keys.sh
docker compose --env-file .env -f docker/docker-compose.yml --profile infra up -d
```

Build and test the backend:

```sh
mvn -B verify
```

Start auth-service with the `demo` profile. The profile creates the demo tenants and accounts and
requires `DEMO_PASSWORD` to be set:

```sh
set -a; . ./.env; set +a
SPRING_PROFILES_ACTIVE=demo java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
```

In a second terminal, start the gateway on port 8080. auth-service keeps the management port from
`.env` (9090), so give the gateway a different one. After that, all `/api` traffic goes through
`http://localhost:8080`:

```sh
set -a; . ./.env; set +a
MANAGEMENT_SERVER_PORT=9080 java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

Platform bootstrap runs by default and requires a real `PLATFORM_ADMIN_PASSWORD`; the example
placeholder is rejected outside the `dev` profile. Set `PLATFORM_BOOTSTRAP_ENABLED=false` when no
platform super admin is needed.

Demo accounts, all using the password from `DEMO_PASSWORD`:

| Tenant | Email | Role |
|---|---|---|
| atlas | admin@atlas.local | TENANT_ADMIN |
| atlas | instructor@atlas.local | INSTRUCTOR |
| atlas | student1@atlas.local | STUDENT |
| atlas | student2@atlas.local | STUDENT |
| acme | admin@acme.local | TENANT_ADMIN |
| acme | instructor@acme.local | INSTRUCTOR |
| acme | student1@acme.local | STUDENT |
| acme | student2@acme.local | STUDENT |

The demo tenants are "Atlas Academy" and "Acme Academy", with the distinct primary colors
`#00B42A` and `#722ED1`.

The seed is idempotent by slug, so it never renames or removes rows that already exist. A local
database created by an earlier version still contains the previous demo tenant alongside the
current ones; start from a clean volume to get exactly the accounts above. The MySQL initialization
script also runs only on an empty volume. See the reset procedure below.

## Reset the demo data

`docker compose down -v` followed by a fresh start recreates the databases and the demo data:

```sh
docker compose --env-file .env -f docker/docker-compose.yml --profile infra down -v
docker compose --env-file .env -f docker/docker-compose.yml --profile infra up -d
```

`down -v` deletes the MySQL and Kafka volumes, so every database and all Kafka data is lost. Start
auth-service with the `demo` profile again afterwards.

## Gateway notes

The gateway validates the access token, strips inbound `X-Tenant*` and `X-User*` headers, rate
limits by client IP or `tenant_id:sub`, and rejects a request whose `Host` subdomain does not match
the token's `tenant_slug`. nginx must keep sending `proxy_set_header Host $host` for that check.

When Redis is unavailable, rate limiting fails open: requests are routed and the gateway logs
`Error calling rate limiter lua`. Token validation, routing and the Host check still apply. See
`docs/adr/004-gateway.md`.
