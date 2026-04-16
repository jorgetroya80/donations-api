# Issues Resolved During Implementation

## Phase 1: Foundation

### Testcontainers 2.x module rename
- **Problem:** `org.testcontainers:postgresql` artifact does not exist in Testcontainers 2.x. Gradle failed to resolve the dependency.
- **Fix:** Renamed to `org.testcontainers:testcontainers-postgresql` which is the 2.x artifact name.

### Testcontainers 2.x PostgreSQLContainer package change
- **Problem:** `org.testcontainers.containers.PostgreSQLContainer` is deprecated in Testcontainers 2.x.
- **Fix:** Switched to `org.testcontainers.postgresql.PostgreSQLContainer` (new package in 2.x).

### Flyway 11.x requires database-specific module
- **Problem:** Flyway 11.x modularized database support. `flyway-core` alone cannot detect PostgreSQL, failing with `FlywayException` at `DatabaseTypeRegister`.
- **Fix:** Added `org.flywaydb:flyway-database-postgresql` as a `runtimeOnly` dependency.

### Missing Spring Data JPA dependency
- **Problem:** The initial project scaffold had no `spring-boot-starter-data-jpa`. JPA entities and repositories require it.
- **Fix:** Added `org.springframework.boot:spring-boot-starter-data-jpa` and `org.jetbrains.kotlin.plugin.jpa` (for no-arg constructor generation).

## Phase 2: Authentication

### ObjectMapper bean unavailable in Spring Boot 4
- **Problem:** `SecurityConfig` injected `com.fasterxml.jackson.databind.ObjectMapper`, but Spring Boot 4 with Jackson 3.x (`tools.jackson`) does not auto-configure the legacy Jackson 2.x `ObjectMapper` as a standalone bean. Build passed but the test context failed with `NoSuchBeanDefinitionException`.
- **Fix:** Removed `ObjectMapper` dependency from `SecurityConfig`. The custom `AuthenticationEntryPoint` writes JSON inline using string interpolation instead.

### Spring Security 7 adds FACTOR_PASSWORD authority
- **Problem:** Login response included an unexpected `FACTOR_PASSWORD` authority alongside the user's roles. This is a Spring Security 7 internal authority from the `CompromisedPasswordChecker` feature.
- **Fix:** Filtered login response authorities to only include those with the `ROLE_` prefix, stripping the prefix for the response.

### Kotlin null safety with JSR305 strict mode
- **Problem:** `GrantedAuthority.getAuthority()` returns `String?` under `-Xjsr305=strict`. Code `it.authority.removePrefix("ROLE_")` failed compilation.
- **Fix:** Changed to `mapNotNull { it.authority }` to handle nullable safely.

### BCrypt hash in seed migration
- **Problem:** The initial Flyway seed migration used a placeholder BCrypt hash that didn't match the password "admin".
- **Fix:** Generated a valid BCrypt hash using `bcrypt.hashpw(b'admin', bcrypt.gensalt(rounds=10))` and updated the migration.

### Jackson null serialization
- **Problem:** Error responses included `"fields":null` when no field-level errors existed, making the JSON noisy.
- **Fix:** Added `spring.jackson.default-property-inclusion: non_null` to `application.yaml`.

### Agent write permission denials
- **Problem:** Both specialist agents (kotlin-specialist, spring-boot-engineer) were denied Write tool permissions, preventing them from creating files directly.
- **Workaround:** Agents produced the correct code in their output; the coordinator (main Claude session) wrote the files manually using the agents' output.

## Phase 3: User Management

### AutoConfigureMockMvc package moved in Spring Boot 4
- **Problem:** `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` does not exist in Spring Boot 4. Tests failed to compile with `Unresolved reference 'web'`.
- **Fix:** Switched to `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (new package in Spring Boot 4).

### PasswordEncoder.encode() returns nullable under -Xjsr305=strict
- **Problem:** `PasswordEncoder.encode()` returns `String?` in Kotlin with `-Xjsr305=strict`, causing assignment type mismatch when assigning to `User.password: String`.
- **Fix:** Added non-null assertion `!!` on all `passwordEncoder.encode()` calls (safe because BCrypt never returns null).

### SecurityContextHolder.authentication nullable under -Xjsr305=strict
- **Problem:** `SecurityContextHolder.getContext().authentication` returns `Authentication?` under strict null safety. Direct `.name` access failed compilation.
- **Fix:** Used safe call `?.name` with fallback `?: throw IllegalStateException("No authenticated user")`.

### MockMvc does not return HTTP cookies
- **Problem:** Integration tests expected session cookies via `result.response.cookies`, but MockMvc doesn't populate HTTP cookies for session tracking. All tests failed with "No session cookie returned".
- **Fix:** Replaced cookie-based session handling with `MockHttpSession`. Extract session via `result.request.getSession(false) as MockHttpSession`, pass to subsequent requests via `.session(session)`.

## Phase 4: Donor Management

No new issues. All patterns from Phase 3 (AutoConfigureMockMvc package, MockHttpSession, null safety) carried over cleanly. Compiled and all tests passed on first attempt.

## Phase 5: Donation Recording

### Test dates must respect @PastOrPresent validation
- **Problem:** Integration test used future date `2026-06-10` in test data, but `@PastOrPresent` on `donationDate` rejected it with 400. Test ran on April 15, 2026.
- **Fix:** Changed test date to `2026-04-10` (past date). Tests with date-based validation must use dates that are in the past relative to when tests run.

## Phase 6: Expense Recording

No new issues. Module mirrors donation pattern (entity, repository, DTOs, service, controller, integration tests). Reused `PaymentMethod` enum from donation package. Compiled and all 16 tests passed on first attempt.

## Phase 7: Financial Reports

No new issues. Four read-only report endpoints (donation summary, expense summary, balance, donor statement) with aggregate JPQL queries. Reused existing repositories with added query methods. All 12 tests passed on first attempt.

## Phase 8: Springdoc/OpenAPI and Production Hardening

### Logout endpoint not in OpenAPI spec
- **Problem:** Test asserted `/api/v1/logout` exists in OpenAPI paths, but logout is handled by Spring Security's `LogoutFilter`, not a `@RestController` mapping. Springdoc only documents controller endpoints.
- **Fix:** Removed `/api/v1/logout` assertion from OpenAPI spec test. All other 14 controller-mapped endpoints verified present.
