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
(variable `RECEIPTS_DIR`). Le dossier est créé au premier dépôt et reste ignoré par Git. Il lit le
prix, le titre et la devise d'un cours auprès de course-service (`COURSE_SERVICE_URL`, par défaut
`http://localhost:8082`).

Avec `-Seed`, `dev-start.ps1` exécute ensuite `scripts/seed-demo.ps1`. Ce script lit
`DEMO_PASSWORD` dans `.env`, se connecte au gateway en tant que `instructor@atlas.local` et
`instructor@acme.local`, crée les cours, modules et leçons décrits dans
`scripts/demo-courses.json`, puis publie les cours marqués `PUBLISHED`. Il n'affiche jamais les
jetons d'accès, affiche un tableau récapitulatif et renvoie un code de sortie non nul en cas
d'erreur.

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

Le serveur de développement conserve l'en-tête `Host` (`changeOrigin: false`) : le gateway compare
le sous-domaine avec l'organisation du jeton. Il ajoute aussi les en-têtes `X-Forwarded-*`
(`xfwd: true`) pour la limitation de débit par adresse IP.

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

The gateway validates the access token, strips inbound `X-Tenant*` and `X-User*` headers, rate
limits by client IP or `tenant_id:sub`, and rejects a request whose `Host` subdomain does not match
the token's `tenant_slug`. nginx must keep sending `proxy_set_header Host $host` for that check.

When Redis is unavailable, rate limiting fails open: requests are routed and the gateway logs
`Error calling rate limiter lua`. Token validation, routing and the Host check still apply. See
`docs/adr/004-gateway.md`.
