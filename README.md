# SANAA

SANAA est une plateforme d'apprentissage en ligne multi-locataire, construite comme projet de
portfolio et d'apprentissage. Les organisations partagent un schéma MySQL et sont isolées par un
discriminateur Hibernate ; les utilisateurs s'authentifient avec des jetons d'accès RS256 de courte
durée ; chaque appel `/api` passe par une passerelle Spring Cloud qui valide le jeton, applique les
limites de débit Redis et route vers le service concerné. Le périmètre visé couvre les cours,
l'inscription, les paiements et la diffusion de médias protégés. Le tableau ci-dessous décrit ce qui
est réellement implémenté aujourd'hui.

## État

| Module | État | Périmètre |
|---|---|---|
| `common` | Terminé | Contexte de locataire et `@TenantId`, claims JWT, modèle de rôles, enveloppe d'événement JSON |
| `auth-service` | Terminé | Comptes globaux, création de créateurs, rotation des jetons de rafraîchissement, équipes et profils, images, pages publiques des créateurs, administration de la plateforme et audit, outbox |
| `gateway` | Terminé | Validation JWT, routage par service propriétaire, limites de débit Redis, nettoyage des en-têtes |
| `course-service` | En cours | Catalogue public et recherche, cours, modules, leçons, inscriptions, avis ; médias et lecture vidéo en attente |
| `payment-service` | En cours | Soumission avec justificatif, validation, outbox Kafka ; le reste du parcours d'achat n'est pas encore accepté |
| `notification-service` | Prévu | Consommateurs Kafka et envoi des courriels |
| `client` | En cours | Application React : identité SANAA, authentification, coque par rôle, place de marché v2, espace apprenant, pages créateurs, interface en français |

Les modules implémentés sont couverts par des tests automatisés. `notification-service` ne contient
pour l'instant qu'un point d'entrée Spring Boot et de la configuration.

## Pile technique

Versions backend résolues par le build et consignées dans la section Infrastructure de
`docs/architecture.md` :

- Java 27
- Spring Boot 4.1.2 (Spring Framework 7, Spring Security 7)
- Spring Cloud 2025.1.4
- Hibernate ORM 7.4.13.Final
- Jackson 3.1.7
- MySQL 8.4 avec migrations Flyway
- Redis 7
- Clients Kafka 4.2.2 (mode KRaft)
- Testcontainers 2.0.5

Frontend : React 19.3.1, TypeScript 5.9.3, Vite 8.3.6, Arco Design 2.66.18, TanStack Query
5.107.1 et Zustand 5.1.0. Voir `docs/adr/005-frontend.md` pour la vérification de compatibilité
React 19, les versions résolues, le découpage des lots et les mesures de contraste.

Voir [docs/architecture.md](docs/architecture.md) pour la conception du système, le modèle de
locataires, le contrat d'API et l'infrastructure. [docs/Design.md](docs/Design.md) est la référence
de conception de l'interface.

## Lancer le projet en local

Prérequis :

- JDK 27, avec `JAVA_HOME` pointant vers cette installation.
- Maven 3.9 ou plus récent.
- Docker avec Compose en cours d'exécution.

Lancer les commandes depuis la racine du dépôt. Les exemples de shell utilisent un shell POSIX ; sous
Windows, utiliser WSL ou Git Bash.

Créer le fichier d'environnement, générer les clés JWT et démarrer les conteneurs d'infrastructure
(MySQL, Redis, Kafka, Mailpit, Prometheus) :

```sh
cp .env.example .env
# Renseigner les mots de passe de base de données et DEMO_PASSWORD dans .env.
sh scripts/gen-jwt-keys.sh
docker compose --env-file .env -f docker/docker-compose.yml --profile infra up -d
```

Compiler et tester le backend :

```sh
mvn -B verify
```

Démarrer auth-service avec le profil `demo`. Ce profil crée les créateurs et les comptes de
démonstration et exige `DEMO_PASSWORD` :

```sh
set -a; . ./.env; set +a
SPRING_PROFILES_ACTIVE=demo java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
```

Dans un second terminal, démarrer la passerelle sur le port 8080. auth-service conserve le port de
gestion défini dans `.env` (9090) ; donner un autre port au gateway. Ensuite, tout le trafic `/api`
passe par `http://localhost:8080` :

```sh
set -a; . ./.env; set +a
MANAGEMENT_SERVER_PORT=9080 java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

Le bootstrap de la plateforme s'exécute par défaut et crée un compte global `SUPER_ADMIN` à partir de
`PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_NAME` et d'un vrai `PLATFORM_ADMIN_PASSWORD` ; la valeur
d'exemple est refusée hors du profil `dev`. Définir `PLATFORM_BOOTSTRAP_ENABLED=false` lorsqu'aucun
super administrateur de plateforme n'est nécessaire. Un courriel de bootstrap déjà utilisé par un
compte non administrateur fait échouer le démarrage au lieu de promouvoir le compte en silence.

Comptes de démonstration, tous avec le mot de passe de `DEMO_PASSWORD` :

| Organisation | Courriel | Rôle |
|---|---|---|
| atlas (organisation) | admin@atlas.local | TENANT_ADMIN |
| atlas (organisation) | instructor1@atlas.local | INSTRUCTOR |
| atlas (organisation) | instructor2@atlas.local | INSTRUCTOR |
| acme (individuel) | admin@acme.local | TENANT_ADMIN |
| aucun (apprenants globaux) | learner1@example.local à learner4@example.local | STUDENT |

Les créateurs de démonstration sont « Atlas Academy » (`ORGANIZATION`) et « Studio Acme »
(`INDIVIDUAL`), avec les couleurs principales `#00B42A` et `#722ED1`. Un apprenant n'appartient à
aucun créateur : le même compte peut suivre des cours des deux organisations.

Le script de démonstration est idempotent par slug : il ne renomme ni ne supprime de lignes
existantes. Une base locale créée par une version antérieure contient encore l'ancien locataire de
démonstration à côté des créateurs actuels ; repartir d'un volume vide pour obtenir exactement les
comptes ci-dessus. Le script d'initialisation MySQL ne s'exécute lui aussi que sur un volume vide.
Voir la procédure de réinitialisation plus bas.

### Déclencheurs d'audit et journalisation binaire

`V2__publisher_model.sql` crée les déclencheurs append-only de `admin_audit_log`. MySQL 8 active la
journalisation binaire par défaut (`log_bin=ON`) et la création d'un déclencheur dans ce mode exige
normalement `SUPER` ou `log_bin_trust_function_creators=1`. Les utilisateurs des services SANAA ne
sont pas `SUPER` ; le serveur doit donc faire confiance à la création de déclencheurs :

- Compose : `docker/docker-compose.yml` démarre MySQL avec
  `command: ["--log-bin-trust-function-creators=1"]`.
- Testcontainers : `AuthFlowIntegrationTest` et `OutboxKafkaIntegrationTest`, qui appliquent la
  migration V2, appellent `.withCommand("--log-bin-trust-function-creators=1")` sur le conteneur
  `mysql:8.4`.
- Un serveur de production doit définir la même option (ou donner `SUPER` à l'utilisateur des
  migrations) avant l'exécution de Flyway, sinon la migration échoue.

L'audit échoue fermé : `AuditService.record` écrit la ligne `admin_audit_log` dans la même
transaction que l'écriture administrative ou la lecture de données personnelles. Si l'insertion
échoue, l'action échoue avec un `ProblemDetail` 500 et rien n'est persisté.

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
gestion 9083) et payment-service (8083, gestion 9084) en arrière-plan, écrit leurs journaux et leurs
PID dans `logs/`, attend que les quatre endpoints de santé répondent, puis affiche les URL et les
comptes de démonstration sans les mots de passe.

payment-service stocke les justificatifs sur le disque local, sous `data/receipts` par défaut
(variable `RECEIPTS_DIR`). auth-service stocke les avatars et les logos sous `data/images` par défaut
(variable `IMAGES_DIR`, `sanaa.images.directory` côté Spring). Les deux dossiers sont créés au premier
dépôt et restent ignorés par Git ; en production, ils doivent pointer vers un volume persistant ou un
stockage objet. payment-service lit le prix, le titre et la devise d'un cours auprès de
course-service (`COURSE_SERVICE_URL`, par défaut `http://localhost:8082`).

auth-service publie ses changements de créateur par une outbox transactionnelle : l'API écrit la
ligne dans MySQL dans la même transaction que la modification, puis un ordonnanceur la publie sur
`sanaa.publishers.v1`. La jauge `sanaa_outbox_unpublished` (actuator) indique le retard. Comme pour
les paiements, Kafka peut être arrêté : la requête répond normalement et le message part au retour du
broker. Le poller se désactive avec `OUTBOX_POLLER_ENABLED=false`, ce que font les tests qui ne
testent pas Kafka.

Les événements de paiement passent par Kafka. À l'approbation, payment-service écrit l'événement dans
sa table outbox dans la même transaction que le paiement, puis un ordonnanceur le publie sur
`sanaa.payments.v1` environ une fois par seconde. course-service consomme `PAYMENT_APPROVED` et crée
l'inscription : elle apparaît dans `GET /api/learner/enrollments` quelques secondes après
l'approbation, sans appel synchrone entre les deux services. L'adresse du broker vient de
`KAFKA_BOOTSTRAP_SERVERS` (par défaut `localhost:29092` pour un broker Docker sur la machine hôte,
`kafka:9092` depuis un conteneur). Kafka fait partie du profil `infra` démarré par `dev-start.ps1`.
Si Kafka est arrêté, l'approbation répond quand même 200 : l'événement reste dans l'outbox et il est
publié au redémarrage du broker, donc l'inscription arrive avec un peu de retard.

Avec `-Seed`, `dev-start.ps1` exécute ensuite `scripts/seed-demo.ps1`. Ce script lit
`DEMO_PASSWORD` dans `.env` et se connecte au gateway en tant qu'instructeur. Il crée les cours,
modules et leçons décrits dans `scripts/demo-courses.json`, puis publie les cours marqués
`PUBLISHED`. Il inscrit les apprenants de démonstration aux cours gratuits, dépose leurs avis et,
lorsque les données le prévoient, la réponse du créateur. Il n'affiche jamais les jetons d'accès,
affiche un tableau récapitulatif et renvoie un code de sortie non nul en cas d'erreur.

Le script est idempotent : un cours dont le titre existe déjà pour cet instructeur est ignoré, une
inscription déjà présente répond 409 et un avis est mis à jour au lieu d'être dupliqué. Une réponse
déjà publiée répond 409 et n'est pas réécrite. On peut donc relancer `-Seed` ou `seed-demo.ps1`
sans créer de doublon.

Contenu de démonstration (10 cours publiés, 2 brouillons, 10 avis dont 2 réponses de créateur) :

| Organisation | Cours publiés | Cours en brouillon |
|---|---|---|
| atlas | JavaScript moderne : les fondamentaux (promotion) ; React et interfaces accessibles ; Python pour l'analyse de données ; Premiers pas en intelligence artificielle (gratuit) ; Git et travail collaboratif (gratuit) ; Découvrir la ligne de commande (gratuit) ; Étalonnage et colorimétrie ; Composer une vidéo avec son smartphone (gratuit) | Tests automatisés avec Vitest |
| acme | Identité visuelle : créer un logo mémorable (gratuit) ; Figma : maquettes et prototypes (promotion) | Photographie de produit au smartphone |

Chaque cours contient deux modules et deux à trois leçons par module, avec une leçon en aperçu
gratuit. Les textes français sont stockés en UTF-8 dans `scripts/demo-courses.json`. Le script peut
aussi être lancé seul, une fois la pile démarrée :

```powershell
.\scripts\seed-demo.ps1
```

## Lancer le frontend

Le frontend v2 n'a qu'une marque, SANAA, et qu'un domaine. Il ne détecte plus d'organisation à partir
du nom d'hôte ni d'un paramètre `?tenant=`, et il n'envoie plus `tenantSlug` à la connexion : un
utilisateur se connecte avec son e-mail et son mot de passe, et un créateur se consulte par son slug
public (`/publishers/:slug`).

Prérequis : Node 20.19 ou plus récent (Node 24 est utilisé en intégration continue) et npm. Le
gateway doit écouter sur `http://localhost:8080`.

```sh
cd client
npm ci
npm run dev
```

Le fichier `client/.env` est facultatif. La seule variable documentée par `client/.env.example` est :

- `VITE_API_TARGET` : cible du proxy `/api`, `http://localhost:8080` par défaut.

Ouvrir ensuite <http://localhost:5173>. Les routes publiques sont `/`, `/courses`,
`/courses/:courseId`, `/categories/:slug`, `/publishers/:slug`, `/instructors/:id`, `/login`,
`/register`, `/become-creator` et `/privacy`. Une fois connecté, `/me`, `/password`, `/learn`,
`/learn/courses`, `/studio/profile`, `/studio/team` et `/admin` s'ajoutent selon le rôle. Les pages
de paiement `/my-payments` et `/payments` restent joignables par URL mais aucun menu n'y mène tant
que le parcours d'achat n'est pas accepté.

La place de marché v2 est décrite dans `docs/adr/005-frontend.md` et `docs/adr/011-marketplace-catalogue.md` :

- `/courses` garde ses filtres dans l'adresse (`q`, `category`, `tag`, `level`, `free`, `sort`,
  `page`), avec une recherche différée de 300 ms, 24 résultats par page et une restauration par les
  boutons précédent et suivant.
- La page de cours propose un seul bouton principal, choisi à partir de
  `GET /api/learner/courses/{id}/purchase-info` : connexion, déjà inscrit, cours de votre propre
  créateur, paiement en attente, inscription gratuite, achat prochainement disponible ou action
  indisponible.
- Les avis sont paginés, la réponse du créateur apparaît sous l'avis et le formulaire préremplit
  l'avis existant (`GET /api/learner/courses/{id}/my-review`).
- `/learn` et `/learn/courses` rassemblent les cours de l'apprenant, ses enseignants, les catégories
  suggérées et la source de chaque inscription (Gratuit, Achat, Accès accordé).
- Chaque page est chargée à la demande ; le point d'entrée reste sous 70 kB minifié.

Le serveur de développement conserve l'en-tête `Host` (`changeOrigin: false`) et ajoute les en-têtes
`X-Forwarded-*` (`xfwd: true`) utilisés par la limitation de débit par adresse IP. Le gateway ne
compare plus le nom d'hôte avec une organisation, donc aucun sous-domaine n'est nécessaire.

Vérifications locales :

```sh
cd client
npm run lint
npm test
npm run build
npx tsc -b
```

## Réinitialiser les données de démonstration

`docker compose down -v` suivi d'un redémarrage recrée les bases et les données de démonstration :

```sh
docker compose --env-file .env -f docker/docker-compose.yml --profile infra down -v
docker compose --env-file .env -f docker/docker-compose.yml --profile infra up -d
```

`down -v` supprime les volumes MySQL et Kafka, donc toutes les bases et toutes les données Kafka sont
perdues. Redémarrer ensuite auth-service avec le profil `demo`.

## Notes sur le gateway

Le gateway valide le jeton d'accès, supprime les en-têtes entrants `X-Tenant*` et `X-User*`, et
limite le débit par adresse IP (routes publiques), par `sub` (routes authentifiées) ou par adresse IP
et leçon (médias). Il n'y a plus de vérification `Host` contre un locataire : un créateur est
sélectionné par son slug dans l'URL publique, pas par sous-domaine, donc nginx n'a pas besoin de
préserver un `Host` particulier.

Les routes publiques sont `POST /api/auth/login|register|refresh|logout`,
`POST /api/auth/publishers`, `GET /api/auth/publishers/slug-available` et `GET /api/public/**`,
ainsi que la lecture des médias et le callback signé de la carte. La création d'un créateur a sa
propre politique, plus stricte. Une session dont le claim `must_change_password` vaut vrai ne peut
appeler que `POST /api/auth/password` et `POST /api/auth/logout` ; tout autre chemin routé répond 403
avec un `ProblemDetail`.

Le gateway applique cette règle en bordure, et les services servlet l'appliquent de nouveau :
`com.sanaa.common.security.MustChangePasswordFilter` est enregistré de façon optionnelle dans la
chaîne de sécurité d'auth-service, de course-service et de payment-service, après l'authentification
par jeton. Un service appelé directement applique donc la même règle et le même corps de problème à
cinq champs.

Lorsque Redis est indisponible, la limitation de débit échoue en laissant passer : les requêtes sont
routées et le gateway journalise `Error calling rate limiter lua`. La validation du jeton et le
routage restent actifs. Voir `docs/adr/004-gateway.md` et `docs/adr/010-publisher-model.md`.
