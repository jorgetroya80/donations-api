# PRD: Dockerize API for Frontend Development

> Source: [jorgetroya80/donations-api#2](https://github.com/jorgetroya80/donations-api/issues/2)

## Problem Statement

Frontend devs building React + Vite frontend (separate repo) need simple way to run donations API and PostgreSQL locally. Currently must install Java 24, Gradle, PostgreSQL manually, understand Spring Boot config, run from source. High barrier for frontend-only devs who need working API to code against.

## Solution

Package Spring Boot API into Docker container via multi-stage Dockerfile (JVM fat JAR). Update `compose.yaml` so `docker compose up` starts API and PostgreSQL with zero local setup. Add CORS config so React frontend (`localhost:5173` via Vite) can make authenticated API calls with session cookies. Add Spring Actuator for container health checks.

## User Stories

1. Frontend dev: run `docker compose up` in API repo → fully working API + database without installing Java or PostgreSQL
2. Frontend dev: API container waits for PostgreSQL healthy before starting → no connection errors on startup
3. Frontend dev: Flyway migrations run automatically on container startup → database schema always up to date
4. Frontend dev: seed admin user (admin/admin) available after startup → immediately log in and test authenticated endpoints
5. Frontend dev: Swagger UI accessible at `http://localhost:8081/swagger-ui/index.html`, → explore and test API endpoints interactively
6. Frontend dev: API accepts requests from `http://localhost:5173`, → Vite dev server calls API without CORS errors
7. Frontend dev: API allows credentials (cookies) in CORS responses → session-based auth works from frontend
8. Frontend dev: preflight OPTIONS requests handled correctly → PUT and DELETE requests work from browser
9. Frontend dev: API container exposes health check endpoint → Docker reports container readiness accurately
10. Frontend dev: rebuild API container with `docker compose up --build` after pulling new API changes → always run latest version
11. API dev: Docker build uses multi-stage builds → final image small (JRE only, no build tools)
12. API dev: `.dockerignore` file → unnecessary files (`.git`, `build/`, IDE files) don't bloat Docker build context
13. API dev: CORS restricted to dev profile only → production deployments don't accidentally allow cross-origin requests
14. API dev: existing tests continue passing after CORS changes → security config not broken
15. API dev: datasource URL configurable via environment variables in Docker → container connects to compose PostgreSQL service instead of localhost

## Implementation Decisions

### Dockerfile
- Multi-stage build: Stage 1 uses Eclipse Temurin 24 JDK for `./gradlew bootJar`, Stage 2 uses Eclipse Temurin 24 JRE for runtime
- Final image exposes port 8081
- Entry point runs fat JAR with configurable Spring profiles and datasource via environment variables

### Docker Compose
- Add `api` service to existing `compose.yaml` alongside `postgres`
- API service depends on postgres with health check condition (`service_healthy`)
- Add health check to postgres service (pg_isready)
- API datasource URL overridden via environment → points to `postgres` service hostname
- API port mapped 8081:8081

### CORS Configuration
- Add `CorsConfigurationSource` bean to `SecurityConfig`
- Allowed origin: `http://localhost:5173` only
- Allowed methods: GET, POST, PUT, DELETE, OPTIONS
- Allow credentials: true (required for session cookies)
- Add `.cors(Customizer.withDefaults())` to security filter chain
- CORS bean gated to dev profile via `@Profile` annotation or conditional property

### Spring Actuator
- Add `spring-boot-starter-actuator` dependency
- Expose `/actuator/health` endpoint (permit without auth in SecurityConfig)
- Used as Docker health check for API container

### .dockerignore
- Excludes: `.gradle/`, `build/`, `.git/`, `.gitignore`, `*.md`, `plans/`, `.claude/`, `.idea/`, `.vscode/`

## Testing Decisions

Good test for this feature verifies external behavior (HTTP response headers, status codes) rather than internal config wiring.

### CORS Integration Test
- Test preflight OPTIONS from `Origin: http://localhost:5173` returns `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials`, `Access-Control-Allow-Methods` headers
- Test requests from disallowed origins rejected
- Follow existing patterns: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration::class)` + `MockMvc`
- Prior art: `OpenApiTest.kt`, `SecurityIntegrationTest.kt`

### Existing Test Suite
- All 88+ existing tests must continue passing after CORS and actuator changes

### Manual Docker Verification
- `docker compose up --build` starts both services
- `curl http://localhost:8081/actuator/health` returns `{"status":"UP"}`
- `curl http://localhost:8081/swagger-ui/index.html` returns 200
- Preflight CORS curl confirms headers present

## Out of Scope

- Frontend app Dockerfile (frontend runs natively via `npm run dev`)
- Frontend app code or repo setup
- Production Docker deployment config (Kubernetes, cloud run, etc.)
- GraalVM native image builds
- CI/CD pipeline for building/pushing Docker images
- Configurable seed credentials (admin/admin hardcoded, dev-only)
- HTTPS/TLS termination in Docker
- Docker image publishing to registry

## Further Notes

- Frontend app lives in separate repo. This PRD only covers API containerization for frontend development support.
- CORS config dev-profile only. Production profile already disables Swagger UI, should not enable CORS.
- Existing `compose.yaml` only has PostgreSQL service. This PRD adds API service alongside it. Frontend devs who previously used compose for DB-only can still run `docker compose up postgres` if needed.