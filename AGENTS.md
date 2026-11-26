# AGENTS.md

Working rules for anyone changing this repository, human or AI agent. Read this first;
follow the links for depth.

## What this is

REST API for a small Spanish church's finances: donors, donations, expenses, financial
reports, and the users who operate it. Session-authenticated, role-based, deployed on
Render with Neon Postgres. A generated TypeScript client is published from
`packages/api-client`.

- Specs and product requirements: `docs/specs/` (GitHub issue #1 is the original PRD)
- Architecture: `docs/architecture.md`
- Decisions and their rationale: `docs/decisions/`
- Implementation plans: `docs/plans/`
- Raw ideas: `docs/ideas/`

## Where documents go

Save new documents here, not in the repo root or `tasks/`. This overrides any skill
default (for example `SPEC.md` in the root, or `tasks/plan.md` / `tasks/todo.md`).

| Kind                          | Folder            | Name                                        |
|-------------------------------|-------------------|---------------------------------------------|
| Idea (raw, not yet approved)  | `docs/ideas/`     | `kebab-subject.md`                          |
| Spec (what and why)           | `docs/specs/`     | `kebab-subject.md`                          |
| Plan and todo (how, in order) | `docs/plans/`     | `kebab-subject.md`, `kebab-subject-todo.md` |
| Decision                      | `docs/decisions/` | `ADR-NNN-kebab-title.md`                    |

Use the same subject name across the idea, spec and plan so they pair up (for example
`ideas/audit-log.md`, `specs/audit-log.md`, `plans/audit-log.md`). Link each document
to the next one in the chain: idea, spec, plan, ADR.

## Stack

| Area      | Choice                                                      |
|-----------|-------------------------------------------------------------|
| Language  | Kotlin 2.4.20 on Java 25 (Temurin, see `.tool-versions`)    |
| Framework | Spring Boot 4.0.5, Spring MVC (not WebFlux), Spring Security 7 |
| Build     | Gradle 9.4.1, Groovy DSL. Always use `./gradlew`            |
| Database  | PostgreSQL 18.3, schema managed by Flyway                   |
| JSON      | Jackson 3 (`tools.jackson.*` packages, not `com.fasterxml`) |
| Tests     | JUnit 5, Spring Boot Test, MockMvc, Testcontainers 2.x      |
| API docs  | springdoc OpenAPI (Swagger UI disabled in `prod`)           |

## Commands

```bash
cp .env.example .env              # first time only, then fill in values
docker compose up -d postgres     # local DB on 127.0.0.1:5432
./gradlew bootRun                 # app on :8081, uses the Compose DB
./gradlew bootTestRun             # app on :8081 with its own Testcontainers DB (no Compose needed)
./gradlew test                    # full test suite (needs Docker running)
./gradlew check                   # what CI runs
./gradlew generateOpenApiDocs     # writes build/openapi/openapi.yaml
```

Run with `SPRING_PROFILES_ACTIVE=dev` to enable CORS for a local frontend
(`APP_CORS_ALLOWED_ORIGINS`, default `http://localhost:8080`).

Swagger UI (non-prod): `http://localhost:8081/swagger-ui.html`.

The seeded user is `admin` / `admin` and must change its password on first login
(V3 and V7 migrations). Every new user is forced through the same password change.

## Layout

```
src/main/kotlin/com/example/donations/
  user/        users, roles, login, password change, login lockout
  donor/       donors and national ID (DNI/NIE) validation
  donation/    donations, duplicate detection, aggregations
  expense/     expenses by category
  report/      read-only financial reports (no entity of its own)
  infrastructure/
    audit/     AuditableEntity base class and the append-only audit log
    config/    SecurityConfig, PasswordChangeRequiredFilter, OpenApiConfig
    error/     GlobalExceptionHandler, ProblemDetail helpers, getOrThrow
    events/    typed application events, EventLogger, request-id filter
    session/   session revocation on password change
src/main/resources/db/migration/   Flyway migrations V1 to V9
src/test/kotlin/com/example/donations/   integration tests, one class per feature
packages/api-client/               generated TypeScript client (pnpm)
```

Each domain package follows the same shape: `Entity.kt`, `EntityRepository.kt`,
`EntityService.kt`, `EntityController.kt`, `EntityDtos.kt`. Copy an existing package
(`expense/` is the simplest) when adding a new one.

## Conventions

### API

- All endpoints live under `/api/v1/`.
- Controllers stay thin: validate with `@Valid`, delegate to the service, map the entity
  to a response DTO with `XxxResponse.from(entity)`. Never return an entity.
- Authorization is declared on the controller with `@PreAuthorize("hasAnyRole(...)")`.
  Roles: `ADMIN` (user management only), `TREASURER` (all financial data and reports),
  `OPERATOR` (donors, donations, expenses), `PASTOR` (reports only). See
  `docs/architecture.md` section 7 before changing who can reach what.
- Paged endpoints return Spring `Page<T>`, serialized through `VIA_DTO` (ADR-003).
- Errors are RFC 9457 `ProblemDetail` responses, produced centrally by
  `GlobalExceptionHandler` (ADR-004). For a missing entity use
  `repository.getOrThrow(id, "Label")`; do not build error bodies by hand.
- Changing the request or response shape changes the published TypeScript client. Treat
  it as a breaking change unless it is purely additive.

### Services

- Class-level `@Transactional(readOnly = true)`, with `@Transactional` on each write
  method.
- Every create or update calls `auditService.record(...)` and emits a typed event through
  `eventLogger.emit(...)`. Look at `ExpenseService.kt` for the pattern.
- Entities extend `AuditableEntity`, which fills `created_by` / `updated_by`.

### Logging and personal data (ADR-005)

- Log only through typed events in `infrastructure/events/AppEvent.kt`, named with the
  OWASP logging vocabulary (`noun_verb:field,field`).
- Donor personal data (names, national IDs, addresses, emails), passwords and session ids
  must never appear in logs. `AppEventPiiGuardTest` fails the build if an event type
  declares such a field. Do not work around it with ad-hoc `log.info` calls.
- Internal ids and amounts are allowed.

### Database

- Schema changes go in a new migration `V{n}__description.sql`. Never edit a migration
  that has been applied; production runs on these.
- Hibernate runs with `ddl-auto: validate`, so entities and migrations must agree or the
  app will not start.
- `open-in-view` is off: load what you need inside the service transaction.

### Kotlin style

- Constructor injection, `private val` dependencies. No field injection in main code.
- Prefer `?.let` for partial updates, as the existing services do.
- Compiler runs with `-Xjsr305=strict`; respect Java nullability annotations.
- Keep changes small and in the existing style. No new abstractions for single-use code.

## Tests

- Tests are integration tests: `@SpringBootTest` + `@AutoConfigureMockMvc` +
  `@Import(TestcontainersConfiguration::class)`, driving the real HTTP API with MockMvc
  against a real Postgres container.
- Log in with the helpers in `TestAuth.kt` (`loginAsAdmin`, `loginActivated`). They handle
  the forced password change.
- Add or update a test for every behaviour change. A bug fix starts with a failing test.
- Docker must be running. If a whole test class fails at context load, re-run once before
  investigating; it is usually a Testcontainers startup flake.

## Git, CI and releases

- Branch from `main`; open a pull request. Do not push to `main` directly.
- Commit messages and PR titles use Conventional Commits (`feat:`, `fix:`, `refactor:`,
  `docs:`, `ci:`, `chore:`; `feat!:` for breaking changes). release-please reads them to
  choose the next version and write `CHANGELOG.md`.
- CI (`.github/workflows/ci.yml`) runs `./gradlew check` on every PR. It must be green
  before merge.
- Merging the release-please PR tags a release, pushes the Docker image to Docker Hub
  (version tag plus `latest`), and publishes the npm client to GitHub Packages
  (`.github/workflows/release.yml`).
- Do not bump `version` in `build.gradle` by hand; release-please owns it.
- Render auto-deploys `main` using `render.yaml` with `SPRING_PROFILES_ACTIVE=prod`.

## Decision records

Significant, hard-to-reverse decisions (auth, data model, deploy and infrastructure,
framework or major dependency, breaking API changes) get an ADR:

1. Copy `docs/decisions/ADR-template.md` to `docs/decisions/ADR-NNN-kebab-title.md`
   using the next free number.
2. Fill in all six sections.
3. Add a row to the index in `docs/decisions/README.md`.
4. Link it from the spec or plan that drove it.

Read the existing ADRs before reopening a settled question:

| ADR | Decision |
|-----|----------|
| 001 | Session-based authentication, not JWT |
| 002 | Deploy on Render with Neon managed Postgres |
| 003 | Page serialization via `VIA_DTO` |
| 004 | RFC 9457 Problem Details for errors |
| 005 | OWASP event logging, donor data excluded by construction |
| 006 | Append-only audit log written by the application |

## Secrets and configuration

- Never commit `.env` or real credentials. `.env.example` lists the variables.
- Database connection comes from `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`;
  defaults in `application.yaml` are for local development only.
- `prod` profile: session cookie `Secure`, Swagger UI and api-docs disabled, JSON (ECS)
  logs.

## Spring Boot 4 gotchas

- Jackson 3 lives in `tools.jackson.*`; old `com.fasterxml.jackson.databind` imports will
  not resolve.
- Testcontainers 2.x artifacts are `testcontainers-junit-jupiter` and
  `testcontainers-postgresql`.
- Flyway needs `spring-boot-starter-flyway` plus `flyway-database-postgresql`.
- Spring Boot's BOM pins an older Kotlin; `build.gradle` overrides `kotlin.version`. Bump
  the plugins and that override together.
