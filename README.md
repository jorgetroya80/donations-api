# Donations API

REST API to handle donations for a small organizations.

## Tech Stack

- **Language:** Kotlin 2.2 on Java 24 (Temurin)
- **Framework:** Spring Boot 4.0.5 with Spring MVC
- **Build:** Gradle 9.4.1 (Groovy DSL)
- **Database:** PostgreSQL 18.3 (via Docker Compose)
- **Migrations:** Flyway
- **Testing:** JUnit 5, Testcontainers, Spring Boot Test
- **Native:** GraalVM native-image support
- **Docs:** OpenAPI / Swagger UI

## Architecture

The API is organized into domain modules with a shared infrastructure layer:

| Module       | Description                                          |
|--------------|------------------------------------------------------|
| **Donors**   | Manage donor records with DNI/NIE validation         |
| **Donations**| Track donations by type and payment method           |
| **Expenses** | Track expenses by category                           |
| **Reports**  | Financial summaries (donations, expenses, balance)   |
| **Users**    | Authentication and role-based access control          |

Infrastructure provides auditing, global error handling, and security configuration.

[More info](/docs/architecture.md) about architecture.

## Prerequisites

- Java 24
- Docker and Docker Compose

## Getting Started

Start the database:

```bash
docker compose up -d
```

Run the application:

```bash
./gradlew bootRun
```

Run tests (uses Testcontainers, no external DB needed):

```bash
./gradlew test
```
