# SANAA

Multi-tenant e-learning SaaS platform.

Status: in development.

See `docs/architecture.md` for system design and `docs/Design.md` for the UI design system.

## Local infrastructure

Copy the example environment file once, generate the JWT keys, then start the infrastructure containers from the repository root:

```sh
cp .env.example .env
sh scripts/gen-jwt-keys.sh
docker compose --env-file .env -f docker/docker-compose.yml --profile infra up -d
```

There is a single `.env` at the repository root. Do not create copies under `docker/`.
