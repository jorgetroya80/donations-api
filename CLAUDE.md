## Tech Stack

- **Language:** Kotlin 2.2 on Java 24 (Temurin)
- **Framework:** Spring Boot 4.0.5 with Spring MVC
- **Build:** Gradle 9.4.1 (Groovy DSL) — use `./gradlew`, not a global install
- **Database:** PostgreSQL 18.3 (via Docker Compose)
- **Testing:** JUnit 5, Testcontainers, Spring Boot Test
- **Native:** GraalVM native-image support via `org.graalvm.buildtools.native`

## Architecture Notes

- **Base package:** `com.example.donations`
- **App entry point:** `DonationsApplication.kt` — standard Spring Boot main class
- **Test entry point:** `TestDonationsApplication.kt` — boots the app with Testcontainers configuration for local dev (`./gradlew -PmainClass=com.example.donations.TestDonationsApplicationKt bootRun` or run from IDE)
- **Database config:** `application.yaml` points to `localhost:5432/donations` — requires the Docker Compose PostgreSQL container
- **Flyway:** Present as a dependency but currently disabled (`spring.flyway.enabled: false`). Migrations go in `src/main/resources/db/migration/` when enabled.
