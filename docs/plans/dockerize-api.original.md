# Plan: Dockerize API for Frontend Development

## Context

Frontend app (React + Vite, separate repo) needs to call this API during development. Goal: frontend devs run `docker compose up` and get API + PostgreSQL ready. No frontend Dockerfile needed — frontend runs natively via `npm run dev`.

## Changes

### 1. Create `Dockerfile` (multi-stage, JVM fat JAR)

**File:** `Dockerfile`

- **Stage 1 (build):** Eclipse Temurin 24 JDK, copy source, run `./gradlew bootJar`
- **Stage 2 (runtime):** Eclipse Temurin 24 JRE, copy JAR from build stage, expose 8081
- Use `.dockerignore` to exclude `.gradle`, `build`, `.git`, etc.

### 2. Create `.dockerignore`

**File:** `.dockerignore`

Exclude: `.gradle/`, `build/`, `.git/`, `*.md`, `plans/`, `.claude/`, `.idea/`

### 3. Update `compose.yaml`

**File:** `compose.yaml`

Add `api` service:
- Build from Dockerfile
- Depends on `postgres`
- Port 8081:8081
- Environment: datasource URL pointing to `postgres` service host
- Health check on postgres before API starts

### 4. Add CORS configuration (dev profile)

**File:** `src/main/kotlin/com/example/donations/infrastructure/config/SecurityConfig.kt`

- Add `CorsConfigurationSource` bean
- Allow origin `http://localhost:5173` (Vite default)
- Allow methods: GET, POST, PUT, DELETE, OPTIONS
- Allow credentials (session cookies)
- Apply via `.cors()` in security filter chain
- Profile-gate to dev only via `application.yaml` property or `@Profile("dev")`

### 5. Existing files to modify

- `SecurityConfig.kt` — add `.cors(Customizer.withDefaults())` to filter chain, add CORS bean
- `compose.yaml` — add api service alongside existing postgres service

## Verification

1. `docker compose up --build` — API + PostgreSQL start, Flyway runs migrations
2. `curl http://localhost:8081/swagger-ui/index.html` — Swagger UI accessible
3. `curl http://localhost:8081/v3/api-docs` — OpenAPI spec returns
4. CORS: `curl -H "Origin: http://localhost:5173" -v http://localhost:8081/api/v1/login` — response includes `Access-Control-Allow-Origin`
5. Existing tests still pass: `./gradlew test`
