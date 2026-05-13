# PRD: Externalize Configuration — Database, Docker, and CORS

## Problem Statement

Database credentials, server ports, and CORS origins are hardcoded in the API source files (`application.yaml`, `SecurityConfig.kt`) and in `compose.yaml`. This makes it impossible to deploy the same image to different environments without modifying source code, and risks committing secrets to version control.

## Solution

Extract all environment-specific values into environment variables with safe defaults for local development. Restructure `compose.yaml` to DB-only for API-standalone development. Introduce `.env.example` as a committed template. CORS allowed origins become a configurable property in the API, not hardcoded.

## User Stories

1. As a developer, I want to run the API locally with a Docker-managed database, so that I don't need a local PostgreSQL installation.
2. As a developer, I want to start the database with `docker compose up`, then run the API with `./gradlew bootRun`, so that I get fast iteration with hot reload.
3. As a developer, I want a `.env.example` file in the API repo, so that I know which environment variables are required when setting up a new environment.
4. As a developer, I want `.env` to be gitignored, so that secrets are never accidentally committed.
5. As a developer, I want `application.yaml` to use environment variable placeholders with local defaults, so that the app runs locally without any extra setup.
6. As a developer, I want CORS allowed origins to come from a config property, so that I can change them per environment without touching code.
7. As an operator, I want to deploy the full stack (frontend + API + database) on a production server by running `docker compose up` from the frontend repo with a `.env` file, so that no source code is needed on the server.
8. As an operator, I want to set strong database credentials in a `.env` file on the production server, so that the same Docker image works in production with different secrets.
9. As an operator, I want a placeholder CORS origin in `.env.example`, so that I know to set the real domain before going live.
10. As an operator, I want the API image to read `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` from environment, so that credentials are injected at runtime.

## Implementation Decisions

### compose.yaml (API repo) — DB-only
- Remove the `app` service from `compose.yaml`
- Keep only the `postgres` service
- Read `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` from a `.env` file
- This file is used when developing the API standalone (`./gradlew bootRun`)

### application.yaml — env var placeholders
- Database URL, username, and password use `${ENV_VAR:default}` syntax with localhost defaults
- Add `app.cors.allowed-origins` property backed by `${APP_CORS_ALLOWED_ORIGINS:http://localhost:8080}`
- Default CORS origin is `http://localhost:8080` (nginx proxy port used by frontend's docker-compose)

### SecurityConfig — configurable CORS
- Replace hardcoded `http://localhost:5173` with `@Value("${app.cors.allowed-origins}")`
- Supports comma-separated list of origins for flexibility

### .env / .env.example (API repo)
- `.env` — local dev values, gitignored
- `.env.example` — committed template with placeholder values and comments
- Variables: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `APP_CORS_ALLOWED_ORIGINS`

### Production deployment
- Uses frontend repo's `docker-compose.yml` (already correct — reads `${POSTGRES_*}` from `.env`)
- API image reads Spring env var overrides automatically (`SPRING_DATASOURCE_*`)
- `SPRING_PROFILES_ACTIVE=prod` already set in frontend's compose
- Operator creates `.env` on server from `.env.example`, sets real credentials and prod CORS origin placeholder

### No changes to
- Testcontainers config — spins up its own DB, independent
- Test credentials in test files — test-only, not secrets
- Dockerfile — no config baked in
- Frontend repo — already correctly externalized

## Testing Decisions

- No new tests required: this is pure configuration externalization
- Existing integration tests (`SecurityIntegrationTest`, `CorsIntegrationTest`) must continue to pass
- `CorsIntegrationTest` uses `http://localhost:5173` hardcoded — update to match new default origin `http://localhost:8080` if CORS origin changes, or verify test still covers the right behavior
- Good test: verify the app starts and serves requests with env-var-supplied DB credentials (Testcontainers already does this)

## Out of Scope

- Secret manager integration (Vault, AWS Secrets Manager) — out of scope, `.env` file on server is sufficient
- Spring Cloud Config or centralized config server
- TLS / HTTPS termination — handled by future reverse proxy setup
- Production domain / URL — placeholder only, real domain TBD
- Flyway migration credentials — Flyway is currently disabled; when enabled it reuses the same datasource credentials
- Frontend repo changes — already correctly externalized

## Further Notes

- Production CORS origin placeholder: `https://your-domain.com` — must be updated before going live
- Prod port mapping: API is `expose`d (not `ports`) in frontend's compose — not reachable directly from host, only via nginx. No change needed.
- Local dev workflow: `docker compose up -d` (DB only) → `./gradlew bootRun`
- Full stack local: use frontend repo's `docker-compose.yml` with API repo's `.env` values
