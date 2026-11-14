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
| `auth-service` | Done | Global accounts, publisher onboarding, refresh rotation, team and profile management, images, public publisher pages, platform administration and audit, outbox publisher |
| `gateway` | Done | JWT validation, routing by owning service, Redis rate limits, header sanitization |
| `course-service` | In progress | Courses, modules, lessons, enrollment read model; media, playback and payment-event consumer pending |
| `payment-service` | In progress | Submission with receipt upload, review workflow, transactional outbox; Kafka publisher and enrollment consumer pending |
| `notification-service` | Planned | Kafka consumers and email delivery |
| `client` | In progress | React single-page application: branding, auth, protected shell, French UI |

The implemented modules are covered by automated tests. `notification-service` currently contains a
Spring Boot entry point and configuration only.

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

Frontend: React 19.3.1, TypeScript 5.9.3, Vite 8.3.6, Arco Design 2.66.18, TanStack Query
5.107.1 and Zustand 5.1.0. See `docs/adr/005-frontend.md` for the React 19 compatibility check and
the resolved versions.

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

Start auth-service with the `demo` profile. The profile creates the demo publishers and accounts and
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

Platform bootstrap runs by default and creates one global `SUPER_ADMIN` from
`PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_NAME` and a real `PLATFORM_ADMIN_PASSWORD`; the example
placeholder is rejected outside the `dev` profile. Set `PLATFORM_BOOTSTRAP_ENABLED=false` when no
platform super admin is needed. A bootstrap email that already belongs to a non-admin account fails
startup instead of promoting it silently.

Demo accounts, all using the password from `DEMO_PASSWORD`:

| Publisher | Email | Role |
|---|---|---|
| atlas (organization) | admin@atlas.local | TENANT_ADMIN |
| atlas (organization) | instructor1@atlas.local | INSTRUCTOR |
| atlas (organization) | instructor2@atlas.local | INSTRUCTOR |
| acme (individual) | admin@acme.local | TENANT_ADMIN |
| none (global learners) | learner1@example.local to learner4@example.local | STUDENT |

The demo publishers are "Atlas Academy" (`ORGANIZATION`) and "Studio Acme" (`INDIVIDUAL`), with the
primary colors `#00B42A` and `#722ED1`. A learner has no publisher: the same account can follow
courses from both.

The seed is idempotent by slug, so it never renames or removes rows that already exist. A local
database created by an earlier version still contains the previous demo tenant alongside the
current ones; start from a clean volume to get exactly the accounts above. The MySQL initialization
script also runs only on an empty volume. See the reset procedure below.

### Audit triggers and binary logging

`V2__publisher_model.sql` creates the append-only `admin_audit_log` triggers. MySQL 8 has binary
logging on by default (`log_bin=ON`), and creating a trigger in that mode normally needs `SUPER` or
`log_bin_trust_function_creators=1`. SANAA's service users are not `SUPER`, so the server must trust
trigger creation:

- Compose: `docker/docker-compose.yml` starts MySQL with
  `command: ["--log-bin-trust-function-creators=1"]`.
- Testcontainers: `AuthFlowIntegrationTest` and `OutboxKafkaIntegrationTest`, which apply the V2
  migration, call `.withCommand("--log-bin-trust-function-creators=1")` on the `mysql:8.4`
  container.
- A production server must set the same option (or give the migration user `SUPER`) before Flyway
  runs, otherwise the migration fails.

Auditing fails closed: `AuditService.record` writes the `admin_audit_log` row in the same
transaction as the admin write or personal-data read. If the insert fails, the action fails with a
500 `ProblemDetail` and nothing is persisted.

## Démarrage local sous Windows

Les scripts `scripts/dev-start.ps1` et `scripts/dev-stop.ps1` sont compatibles PowerShell 5.1. Ils
lisent le fichier `.env` de la racine (sans jamais afficher ses valeurs) et supposent que
`DEMO_PASSWORD` et `PLATFORM_ADMIN_PASSWORD` y sont définis, comme dans `.env.example`.

```powershell
# Démarre l'infrastructure, auth-service, le gateway, course-service et payment-service.
.\scripts\dev-start.ps1

# Démarre aussi le serveur Vite sur http://localhost:5173.
.\scripts\dev-start.ps1 -Client

# Remplit les cours de démonstration une fois la pile démarrée.
.\scripts\dev-start.ps1 -Seed

# Combine les deux options.
.\scripts\dev-start.ps1 -Seed -Client

# Arrête les processus applicatifs et laisse l'infrastructure Docker en marche.
.\scripts\dev-stop.ps1

# Arrête aussi les conteneurs Docker.
.\scripts\dev-stop.ps1 -Infra
```

`dev-start.ps1` vérifie que Docker répond, démarre l'infrastructure seulement si elle n'est pas
saine, puis ne reconstruit les jars que s'ils sont absents. Il lance auth-service (profils
`demo,dev`, API 8081, gestion 9081), le gateway (8080, gestion 9082), course-service (8082,
gestion 9083) et payment-service (8083, gestion 9084) en arrière-plan, écrit leurs journaux et
leurs PID dans `logs/`, attend que les quatre endpoints de santé répondent, puis affiche les URLs
et les comptes de démonstration sans les mots de passe.

payment-service stocke les justificatifs sur le disque local, sous `data/receipts` par défaut
(variable `RECEIPTS_DIR`). auth-service stocke les avatars et les logos sous `data/images` par défaut
(variable `IMAGES_DIR`, `sanaa.images.directory` côté Spring). Les deux dossiers sont créés au premier
dépôt et restent ignorés par Git ; en production, ils doivent pointer vers un volume persistant ou
un stockage objet. payment-service lit le prix, le titre et la devise d'un cours auprès de
course-service (`COURSE_SERVICE_URL`, par défaut `http://localhost:8082`).

auth-service publie ses changements de publisher par un outbox transactionnel : l'API écrit la ligne
dans MySQL dans la même transaction que la modification, puis un ordonnanceur la publie sur
`sanaa.publishers.v1`. La jauge `sanaa_outbox_unpublished` (actuator) indique le retard. Comme pour
les paiements, Kafka peut être arrêté : la requête répond normalement et le message part au retour du
broker. Le poller se désactive avec `OUTBOX_POLLER_ENABLED=false`, ce que font les tests qui ne
testent pas Kafka.

Les événements de paiement passent par Kafka. À l'approbation, payment-service écrit l'événement
dans sa table outbox dans la même transaction que le paiement, puis un ordonnanceur le publie sur
`sanaa.payments.v1` environ une fois par seconde. course-service consomme `PAYMENT_APPROVED` et crée
l'inscription : elle apparaît dans `GET /api/enrollments/me` quelques secondes après l'approbation,
sans appel synchrone entre les deux services. L'adresse du broker vient de
`KAFKA_BOOTSTRAP_SERVERS` (par défaut `localhost:29092` pour un broker Docker sur la machine hôte,
`kafka:9092` depuis un conteneur). Kafka fait partie du profil `infra` démarré par `dev-start.ps1`.
Si Kafka est arrêté, l'approbation répond quand même 200 : l'événement reste dans l'outbox et il est
publié au redémarrage du broker, donc l'inscription arrive avec un peu de retard.

Avec `-Seed`, `dev-start.ps1` exécute ensuite `scripts/seed-demo.ps1`. Ce script lit
`DEMO_PASSWORD` dans `.env` et se connecte au gateway en tant qu'instructeur. Il crée les cours,
modules et leçons décrits dans `scripts/demo-courses.json`, puis publie les cours marqués
`PUBLISHED`. Il n'affiche jamais les jetons d'accès, affiche un tableau récapitulatif et renvoie un
code de sortie non nul en cas d'erreur.

> Le modèle d'identité v2 est arrivé avant la mise à jour du script : `seed-demo.ps1` envoie encore
> `tenantSlug` à la connexion et utilise l'ancien compte `instructor@acme.local`, supprimé du jeu de
> démonstration. Il peut donc échouer jusqu'à la tâche course-service, qui le réécrit avec les
> nouveaux comptes `instructor1@atlas.local` et `admin@acme.local` et sans `tenantSlug`. Le reste de
> la pile (authentification, publishers, pages publiques) fonctionne sans lui.

Le script est idempotent : un cours dont le titre existe déjà pour cet instructeur est ignoré, ce
qui permet de relancer `-Seed` ou `seed-demo.ps1` sans créer de doublon.

Contenu de démonstration, deux cours publiés et un brouillon par organisation :

| Organisation | Cours publiés | Cours en brouillon |
|---|---|---|
| atlas | Montage vidéo : les bases ; Étalonnage et colorimétrie | Son et mixage pour la vidéo |
| acme | Gestion de projet agile ; Excel pour la finance | Communication professionnelle |

Chaque cours contient deux modules et deux à trois leçons par module, avec une leçon en aperçu
gratuit. Les textes français sont stockés en UTF-8 dans `scripts/demo-courses.json`. Le script peut
aussi être lancé seul, une fois la pile démarrée :

```powershell
.\scripts\seed-demo.ps1
```

## Lancer le frontend

> Le dossier `client` est encore l'application v1 : elle choisit une organisation avant la connexion
> et envoie `tenantSlug` au gateway. Avec l'API v2 (connexion par e-mail et mot de passe, sans
> organisation), la connexion ne fonctionnera pas tant que la tâche frontend n'aura pas réécrit ce
> dossier. C'est attendu ; le reste du parcours se vérifie avec `curl.exe` ou un client HTTP.

Prérequis : Node 20.19 ou plus récent (Node 24 est utilisé en intégration continue) et npm. Le
gateway doit écouter sur `http://localhost:8080`.

```sh
cd client
npm ci
npm run dev
```

Le fichier `client/.env` est facultatif. Sans lui, `http://localhost:5173` affiche le sélecteur
d'organisation ; `client/.env.example` documente les variables disponibles :

- `VITE_API_TARGET` : cible du proxy `/api`, `http://localhost:8080` par défaut.
- `VITE_BASE_DOMAIN` : domaine de base utilisé pour lire l'organisation dans le nom d'hôte,
  `localhost` par défaut.
- `VITE_DEFAULT_TENANT` : exemple facultatif (`atlas`) pour présélectionner une organisation quand
  le nom d'hôte et `?tenant=` n'en fournissent aucune. Laisser la valeur vide ou commentée pour
  obtenir le sélecteur.

Ouvrir ensuite :

- <http://localhost:5173> pour choisir une organisation ;
- <http://localhost:5173/?tenant=atlas> pour ouvrir directement Atlas Academy ;
- <http://atlas.localhost:5173> pour Atlas Academy ;
- <http://acme.localhost:5173> pour Acme Academy.

Le paramètre `?tenant=` est conservé lors des redirections internes (connexion vers l'accueil,
déconnexion, garde d'authentification), donc un rechargement sur `/?tenant=atlas` reste valide.

Le serveur de développement conserve l'en-tête `Host` (`changeOrigin: false`) : dans la version v1,
le gateway comparait le sous-domaine avec l'organisation du jeton. Le gateway v2 ne fait plus cette
comparaison, puisque les pages publiques utilisent un slug dans l'URL. Vite ajoute aussi les en-têtes
`X-Forwarded-*` (`xfwd: true`) pour la limitation de débit par adresse IP.

Vérifications locales :

```sh
cd client
npm run lint
npm test
npm run build
```

## Reset the demo data

`docker compose down -v` followed by a fresh start recreates the databases and the demo data:

```sh
docker compose --env-file .env -f docker/docker-compose.yml --profile infra down -v
docker compose --env-file .env -f docker/docker-compose.yml --profile infra up -d
```

`down -v` deletes the MySQL and Kafka volumes, so every database and all Kafka data is lost. Start
auth-service with the `demo` profile again afterwards.

## Gateway notes

The gateway validates the access token, strips inbound `X-Tenant*` and `X-User*` headers, and rate
limits by client IP (public routes), by `sub` (authenticated routes) or by client IP and lesson
(media). There is no `Host`-versus-tenant check any more: a publisher is selected by slug in the
public URL, not by subdomain, so nginx does not need to preserve a special `Host` value.

Public routes are `POST /api/auth/login|register|refresh|logout`, `POST /api/auth/publishers`,
`GET /api/auth/publishers/slug-available` and `GET /api/public/**`, plus media playback and the
signed card callback. Publisher creation has its own stricter policy. A session whose
`must_change_password` claim is true can only call `POST /api/auth/password` and
`POST /api/auth/logout`; every other routed path returns 403 with a `ProblemDetail`.

The gateway enforces that rule at the edge, and the servlet services enforce it again:
`com.sanaa.common.security.MustChangePasswordFilter` is registered opt-in in the security chain of
auth-service, course-service and payment-service, after bearer authentication. A service called
directly therefore applies the same rule and the same five-field problem body.

When Redis is unavailable, rate limiting fails open: requests are routed and the gateway logs
`Error calling rate limiter lua`. Token validation and routing still apply. See
`docs/adr/004-gateway.md` and `docs/adr/010-publisher-model.md`.
