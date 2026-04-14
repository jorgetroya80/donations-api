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
