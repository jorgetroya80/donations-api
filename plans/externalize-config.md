# Plan: Externalize Configuration — Database, Docker, and CORS

> Source PRD: GitHub issue #12 / docs/PRD-4.md

## Architectural decisions

- **Env var convention**: Spring Boot native (`SPRING_DATASOURCE_URL` etc.) for datasource; custom `APP_CORS_ALLOWED_ORIGINS` for CORS
- **Default values**: all env vars have safe localhost defaults so `./gradlew bootRun` works out of the box with no `.env` needed
- **compose.yaml scope**: DB-only — API is never run inside this compose file; full stack uses frontend repo's `docker-compose.yml`
- **CORS origin**: single configurable property in `application.yaml`, injected into `SecurityConfig` — no logic change, only source of value changes
- **Secret storage**: `.env` file on server, gitignored; `.env.example` committed as template

---

## Phase 1: Externalize DB credentials and restructure compose.yaml

**User stories**: 1, 2, 3, 4, 5, 7, 8, 10

### What to build

Restructure `compose.yaml` to run only the `postgres` service. DB credentials are read from a `.env` file (gitignored). `application.yaml` datasource config uses `${ENV_VAR:default}` placeholders so the app connects to `localhost:5432` by default when run natively. Add `.env.example` with placeholder values and comments. Add `.env` to `.gitignore`.

Developer workflow after this phase: `docker compose up -d` → `./gradlew bootRun`.

### Acceptance criteria

- [ ] `compose.yaml` contains only the `postgres` service (no `app` service)
- [ ] `compose.yaml` reads `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` from environment / `.env`
- [ ] `application.yaml` datasource URL, username, and password use `${...}` with localhost defaults
- [ ] `.env` exists locally with dev values and is listed in `.gitignore`
- [ ] `.env.example` is committed with all required variables and inline comments
- [ ] `./gradlew bootRun` connects to the Dockerized DB without any manual config
- [ ] All existing integration tests pass

---

## Phase 2: Externalize CORS allowed origins

**User stories**: 6, 9

### What to build

Add `app.cors.allowed-origins` property to `application.yaml` backed by `${APP_CORS_ALLOWED_ORIGINS:http://localhost:8080}`. Inject this value into `SecurityConfig` via `@Value`. The default `http://localhost:8080` matches the nginx port in the frontend's `docker-compose.yml`. Add `APP_CORS_ALLOWED_ORIGINS` to `.env.example` with a prod placeholder comment.

Update `CorsIntegrationTest` allowed-origin assertion to match the new default (`http://localhost:8080`).

### Acceptance criteria

- [ ] `SecurityConfig` reads CORS origin from `@Value("${app.cors.allowed-origins}")` — no hardcoded string
- [ ] `application.yaml` has `app.cors.allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:8080}`
- [ ] `.env.example` includes `APP_CORS_ALLOWED_ORIGINS=https://your-domain.com` with comment
- [ ] `CorsIntegrationTest` passes with updated origin
- [ ] Setting `APP_CORS_ALLOWED_ORIGINS=http://localhost:5173` in `.env` overrides the default correctly
