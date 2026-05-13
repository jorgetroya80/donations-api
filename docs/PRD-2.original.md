# PRD: Dockerize API for Frontend Development

> Source: [jorgetroya80/donations-api#2](https://github.com/jorgetroya80/donations-api/issues/2)

## Problem Statement

Frontend developers building the React + Vite frontend application (in a separate repository) need a simple way to run the donations API and its PostgreSQL database locally. Currently, they must install Java 24, Gradle, and PostgreSQL manually, understand Spring Boot configuration, and run the app from source. This is a high barrier for frontend-only developers who just need a working API to code against.

## Solution

Package the Spring Boot API into a Docker container image via a multi-stage Dockerfile (JVM fat JAR). Update `compose.yaml` so that `docker compose up` starts both the API and PostgreSQL with zero local setup. Add CORS configuration so the React frontend (running on `localhost:5173` via Vite) can make authenticated API calls with session cookies. Add Spring Actuator for container health checks.

## User Stories

1. As a frontend developer, I want to run `docker compose up` in the API repo, so that I get a fully working API + database without installing Java or PostgreSQL
2. As a frontend developer, I want the API container to wait for PostgreSQL to be healthy before starting, so that I don't see connection errors on startup
3. As a frontend developer, I want Flyway migrations to run automatically on container startup, so that the database schema is always up to date
4. As a frontend developer, I want a seed admin user (admin/admin) available after startup, so that I can immediately log in and test authenticated endpoints
5. As a frontend developer, I want Swagger UI accessible at `http://localhost:8081/swagger-ui/index.html`, so that I can explore and test API endpoints interactively
6. As a frontend developer, I want the API to accept requests from `http://localhost:5173`, so that my Vite dev server can call the API without CORS errors
7. As a frontend developer, I want the API to allow credentials (cookies) in CORS responses, so that session-based authentication works from my frontend
8. As a frontend developer, I want preflight OPTIONS requests handled correctly, so that PUT and DELETE requests work from the browser
9. As a frontend developer, I want the API container to expose a health check endpoint, so that Docker can report container readiness accurately
10. As a frontend developer, I want to rebuild the API container with `docker compose up --build` after pulling new API changes, so that I always run the latest version
11. As an API developer, I want the Docker build to use multi-stage builds, so that the final image is small (JRE only, no build tools)
12. As an API developer, I want a `.dockerignore` file, so that unnecessary files (`.git`, `build/`, IDE files) don't bloat the Docker build context
13. As an API developer, I want CORS restricted to dev profile only, so that production deployments don't accidentally allow cross-origin requests
14. As an API developer, I want existing tests to continue passing after CORS changes, so that the security configuration isn't broken
15. As an API developer, I want the datasource URL configurable via environment variables in Docker, so that the container connects to the compose PostgreSQL service instead of localhost

## Implementation Decisions

### Dockerfile
- Multi-stage build: Stage 1 uses Eclipse Temurin 24 JDK for `./gradlew bootJar`, Stage 2 uses Eclipse Temurin 24 JRE for runtime
- Final image exposes port 8081
- Entry point runs the fat JAR with configurable Spring profiles and datasource via environment variables

### Docker Compose
- Add `api` service to existing `compose.yaml` alongside `postgres`
- API service depends on postgres with health check condition (`service_healthy`)
- Add health check to postgres service (pg_isready)
- API datasource URL overridden via environment to point to `postgres` service hostname
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

A good test for this feature verifies external behavior (HTTP response headers, status codes) rather than internal configuration wiring.

### CORS Integration Test
- Test preflight OPTIONS request from `Origin: http://localhost:5173` returns `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials`, and `Access-Control-Allow-Methods` headers
- Test that requests from disallowed origins are rejected
- Follow existing test patterns: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration::class)` + `MockMvc`
- Prior art: `OpenApiTest.kt`, `SecurityIntegrationTest.kt`

### Existing Test Suite
- All 88+ existing tests must continue to pass after CORS and actuator changes

### Manual Docker Verification
- `docker compose up --build` starts both services
- `curl http://localhost:8081/actuator/health` returns `{"status":"UP"}`
- `curl http://localhost:8081/swagger-ui/index.html` returns 200
- Preflight CORS curl confirms headers present

## Out of Scope

- Frontend application Dockerfile (frontend runs natively via `npm run dev`)
- Frontend application code or repository setup
- Production Docker deployment configuration (Kubernetes, cloud run, etc.)
- GraalVM native image builds
- CI/CD pipeline for building/pushing Docker images
- Configurable seed credentials (admin/admin is hardcoded, dev-only)
- HTTPS/TLS termination in Docker
- Docker image publishing to a registry

## Further Notes

- The frontend application will live in a separate repository. This PRD only covers the API containerization needed to support frontend development.
- CORS configuration is dev-profile only. Production profile already disables Swagger UI and should not enable CORS.
- The existing `compose.yaml` only has a PostgreSQL service. This PRD adds the API service alongside it. Frontend devs who previously used compose for DB-only can still run `docker compose up postgres` if needed.
