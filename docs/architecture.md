# Software Architecture — Donations API v1

**Version:** 1.2.0 | **Stack:** Kotlin 2.2 · Spring Boot 4.0.5 · PostgreSQL 18.3

---

## Table of Contents

1. [Overview](#1-overview)
2. [Network Architecture](#2-network-architecture)
3. [Component Architecture](#3-component-architecture)
4. [Domain Model (Classes)](#4-domain-model-classes)
5. [Database Architecture](#5-database-architecture)
6. [Data Flows](#6-data-flows)
7. [Security and Access Control](#7-security-and-access-control)
8. [Interaction Flows (Sequence)](#8-interaction-flows-sequence)
9. [Package Structure](#9-package-structure)

---

## 1. Overview

The application is a **REST API** for church financial management. It manages donors, donations, expenses, and generates financial reports. It exposes protected endpoints secured with session-based authentication and role-based access control.

```
┌─────────────────────────────────────────────────────────┐
│                    Clients                              │
│          (Browser / Mobile / Postman)                   │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTPS  :8081
                       ▼
┌─────────────────────────────────────────────────────────┐
│              Donations API (Spring Boot)                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐   │
│  │   Auth   │  │  Users   │  │ Donors / │  │Reports │   │
│  │Controller│  │Controller│  │Donations │  │        │   │
│  └──────────┘  └──────────┘  │ Expenses │  └────────┘   │
│                              └──────────┘               │
└──────────────────────┬──────────────────────────────────┘
                       │ JDBC :5432
                       ▼
┌─────────────────────────────────────────────────────────┐
│              PostgreSQL 18.3 (Docker)                   │
│         Database: donations                             │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Network Architecture

```mermaid
graph TB
    subgraph "Client"
        B[Browser / App]
    end

    subgraph "Docker Host"
        subgraph "API Container  :8081"
            APP[Spring Boot App<br/>Kotlin 2.2 / JVM 24]
            SW[Swagger UI<br/>/swagger-ui/index.html]
            ACT[Actuator<br/>/actuator/health]
        end

        subgraph "DB Container  :5432"
            PG[PostgreSQL 18.3<br/>db: donations]
        end
    end

    B -->|"HTTP/HTTPS :8081<br/>JSON + JSESSIONID cookie"| APP
    B -->|"GET /swagger-ui/**<br/>(dev only)"| SW
    APP -->|"JDBC TCP :5432<br/>HikariCP pool"| PG
    APP -->|"Flyway migrations<br/>(on startup)"| PG
```

### Ports and Protocols

| Service | Port | Protocol | Access |
|---------|------|----------|--------|
| REST API | 8081 | HTTP (→ HTTPS in prod) | Public (auth required) |
| Swagger UI | 8081 | HTTP | `dev` profile only |
| Actuator health | 8081 | HTTP | Public |
| PostgreSQL | 5432 | TCP / JDBC | Internal only |

### Environment Variables (production)

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/donations
SPRING_DATASOURCE_USERNAME=donations
SPRING_DATASOURCE_PASSWORD=***
APP_CORS_ALLOWED_ORIGINS=https://my-frontend.com
SPRING_PROFILES_ACTIVE=prod   # disables Swagger
```

---

## 3. Component Architecture

```mermaid
graph TB
    subgraph "HTTP Layer"
        AC[AuthController]
        UC[UserController]
        DC[DonorController]
        DOC[DonationController]
        EC[ExpenseController]
        RC[ReportController]
    end

    subgraph "Security"
        SEC[SecurityConfig<br/>Session + CORS + BCrypt]
        UDS[AppUserDetailsService]
        GEH[GlobalExceptionHandler]
    end

    subgraph "Business Logic"
        US[UserService]
        DS[DonorService]
        DOS[DonationService]
        ES[ExpenseService]
        RS[ReportService]
    end

    subgraph "Data Access"
        UR[UserRepository]
        DR[DonorRepository]
        DOR[DonationRepository]
        ER[ExpenseRepository]
    end

    subgraph "Infrastructure"
        AUD[AuditorAwareConfig<br/>createdBy / updatedBy]
        OAP[OpenApiConfig<br/>Swagger]
        FLY[Flyway<br/>V1–V6 migrations]
    end

    subgraph "Database"
        PG[(PostgreSQL)]
    end

    AC --> US
    UC --> US
    DC --> DS
    DOC --> DOS
    EC --> ES
    RC --> RS

    US --> UR
    DS --> DR
    DOS --> DOR
    DOS --> DR
    ES --> ER
    RS --> DOR
    RS --> ER
    RS --> DR

    UDS --> UR
    SEC --> UDS

    UR --> PG
    DR --> PG
    DOR --> PG
    ER --> PG
    FLY --> PG

    AUD -.->|"populates createdBy/updatedAt"| UR
    AUD -.->|"populates createdBy/updatedAt"| DR
    AUD -.->|"populates createdBy/updatedAt"| DOR
    AUD -.->|"populates createdBy/updatedAt"| ER
```

### Layer Responsibilities

| Layer | Classes | Responsibility |
|-------|---------|----------------|
| **Controllers** | `*Controller` | Receive HTTP, validate input, delegate to service, map response |
| **Services** | `*Service` | Business logic, transactions, domain rules |
| **Repositories** | `*Repository` | SQL queries via Spring Data JPA |
| **Entities** | `User`, `Donor`, `Donation`, `Expense` | Persisted domain model |
| **DTOs** | `*Request`, `*Response` | API contracts (input/output) |
| **Security** | `SecurityConfig`, `AppUserDetailsService` | Authentication, authorization, sessions |
| **Infrastructure** | `GlobalExceptionHandler`, `AuditorAwareConfig` | Cross-cutting concerns: errors, auditing |

---

## 4. Domain Model (Classes)

```mermaid
classDiagram
    class AuditableEntity {
        <<MappedSuperclass>>
        +id: Long
        +createdBy: String?
        +createdAt: Instant?
        +updatedBy: String?
        +updatedAt: Instant?
    }

    class User {
        +username: String
        +password: String
        +active: Boolean
        +roles: Set~Role~
    }

    class Role {
        <<enumeration>>
        ADMIN
        TREASURER
        PASTOR
        OPERATOR
    }

    class Donor {
        +fullName: String
        +dniNie: String
        +email: String?
        +phone: String?
        +address: String?
        +active: Boolean
    }

    class Donation {
        +amount: BigDecimal
        +donationDate: LocalDate
        +donationType: DonationType
        +paymentMethod: PaymentMethod
        +notes: String?
    }

    class Expense {
        +amount: BigDecimal
        +expenseDate: LocalDate
        +category: ExpenseCategory
        +description: String
        +vendor: String?
        +paymentMethod: PaymentMethod
    }

    class DonationType {
        <<enumeration>>
        TITHE
        OFFERING
        SPECIAL_OFFERING
        OTHER
    }

    class ExpenseCategory {
        <<enumeration>>
        RENT
        UTILITIES
        SALARIES
        SUPPLIES
        MISSIONS
        MAINTENANCE
        OTHER
    }

    class PaymentMethod {
        <<enumeration>>
        CASH
        BANK_TRANSFER
    }

    AuditableEntity <|-- User
    AuditableEntity <|-- Donor
    AuditableEntity <|-- Donation
    AuditableEntity <|-- Expense

    User "1" --> "*" Role : has
    Donor "1" --> "0..*" Donation : makes
    Donation "0..1" --> "1" Donor : belongs to
    Donation --> DonationType : is of type
    Donation --> PaymentMethod : paid with
    Expense --> ExpenseCategory : category
    Expense --> PaymentMethod : paid with
```

---

## 5. Database Architecture

### Entity-Relationship Diagram

```mermaid
erDiagram
    users {
        bigint id PK
        varchar username UK
        varchar password
        boolean active
        varchar created_by
        timestamp created_at
        varchar updated_by
        timestamp updated_at
    }

    user_roles {
        bigint user_id FK
        varchar role
    }

    donors {
        bigint id PK
        varchar full_name
        varchar dni_nie UK
        varchar email
        varchar phone
        text address
        boolean active
        varchar created_by
        timestamp created_at
        varchar updated_by
        timestamp updated_at
    }

    donations {
        bigint id PK
        decimal amount
        date donation_date
        varchar donation_type
        varchar payment_method
        bigint donor_id FK
        text notes
        varchar created_by
        timestamp created_at
        varchar updated_by
        timestamp updated_at
    }

    expenses {
        bigint id PK
        decimal amount
        date expense_date
        varchar category
        text description
        varchar vendor
        varchar payment_method
        varchar created_by
        timestamp created_at
        varchar updated_by
        timestamp updated_at
    }

    users ||--o{ user_roles : "has roles"
    donors ||--o{ donations : "makes"
    donations }o--|| donors : "from donor"
```

### Indexes and Constraints

| Table | Column | Type |
|-------|--------|------|
| `users` | `username` | UNIQUE |
| `donors` | `dni_nie` | UNIQUE |
| `donations` | `donation_date` | INDEX |
| `donations` | `donor_id` | INDEX + FK → donors |
| `user_roles` | `(user_id, role)` | PRIMARY KEY |

### Flyway Migrations

```
V1__create_schema_baseline.sql   → empty baseline
V2__create_users_and_roles.sql   → users + user_roles tables
V3__seed_admin_user.sql          → default admin user (BCrypt)
V4__create_donors.sql            → donors table
V5__create_donations.sql         → donations table + indexes
V6__create_expenses.sql          → expenses table + index
```

---

## 6. Data Flows

### General HTTP Request Flow

```mermaid
flowchart TD
    A[Client sends HTTP Request] --> B{Public endpoint?}
    B -->|/login, /health, /swagger-ui| C[Process without auth]
    B -->|All other endpoints| D{Valid JSESSIONID?}
    D -->|No| E[401 Unauthorized]
    D -->|Yes| F{Has required role?}
    F -->|No| G[403 Forbidden]
    F -->|Yes| H[Controller receives request]
    H --> I{Bean validation OK?}
    I -->|No| J[400 Bad Request + field errors]
    I -->|Yes| K[Service executes logic]
    K --> L{Exception?}
    L -->|NotFoundException| M[404 Not Found]
    L -->|IllegalStateException| N[409 Conflict]
    L -->|Other| O[500 Internal Server Error]
    L -->|No| P[Repository → PostgreSQL]
    P --> Q[JSON response to client]
    C --> Q
```

### Donation Flow with Duplicate Detection

```mermaid
flowchart TD
    A[POST /api/v1/donations] --> B[Validate CreateDonationRequest]
    B -->|invalid| C[400 Bad Request]
    B -->|valid| D{donorId present?}
    D -->|Yes| E[Verify donor exists]
    E -->|Not found| F[404 Not Found]
    E -->|Found| G[Search for duplicate]
    D -->|No| G
    G -->|"Same donor+amount+date+type?"| H{Is duplicate?}
    H -->|No| I[Save donation]
    H -->|Yes and confirmDuplicate=false| J["200 OK<br/>saved=false, duplicateWarning=true"]
    H -->|Yes and confirmDuplicate=true| K[Save with warning]
    I --> L["201 Created<br/>saved=true, duplicateWarning=false"]
    K --> M["201 Created<br/>saved=true, duplicateWarning=true"]
```

### Balance Report Flow

```mermaid
flowchart LR
    A["GET /api/v1/reports/balance<br/>?from=2024-01-01&to=2024-12-31"] --> B[ReportController]
    B --> C[ReportService.balance]
    C --> D["DonationRepository<br/>SUM(amount) WHERE date BETWEEN"]
    C --> E["ExpenseRepository<br/>SUM(amount) WHERE date BETWEEN"]
    D --> F[totalIncome]
    E --> G[totalExpenses]
    F --> H["netBalance = totalIncome − totalExpenses"]
    G --> H
    H --> I["BalanceResponse JSON"]
```

---

## 7. Security and Access Control

### Role Model

```mermaid
graph LR
    subgraph Roles
        ADMIN
        TREASURER
        PASTOR
        OPERATOR
    end

    subgraph Endpoints
        U["/api/v1/users<br/>User management"]
        D["/api/v1/donors<br/>Donor management"]
        DON["/api/v1/donations<br/>Donation recording"]
        EXP["/api/v1/expenses<br/>Expense recording"]
        REP["/api/v1/reports<br/>Financial reports"]
        AUTH["/api/v1/login<br/>/api/v1/logout"]
        ME["/api/v1/users/me/password<br/>Change own password"]
    end

    ADMIN --> U
    ADMIN -.->|"not by default"| D

    OPERATOR --> D
    OPERATOR --> DON
    OPERATOR --> EXP

    TREASURER --> D
    TREASURER --> DON
    TREASURER --> EXP
    TREASURER --> REP

    PASTOR --> REP

    ADMIN --> ME
    TREASURER --> ME
    PASTOR --> ME
    OPERATOR --> ME

    ADMIN --> AUTH
    TREASURER --> AUTH
    PASTOR --> AUTH
    OPERATOR --> AUTH
```

### Authentication Mechanism

```mermaid
sequenceDiagram
    actor C as Client
    participant AC as AuthController
    participant AM as AuthenticationManager
    participant UDS as AppUserDetailsService
    participant UR as UserRepository
    participant SS as SecurityContext (Session)

    C->>AC: POST /api/v1/login {username, password}
    AC->>AM: authenticate(username, password)
    AM->>UDS: loadUserByUsername(username)
    UDS->>UR: findByUsername(username)
    UR-->>UDS: User entity
    UDS-->>AM: UserDetails (with ROLE_* authorities)
    AM-->>AC: Authentication OK
    AC->>SS: Store SecurityContext in HttpSession
    AC-->>C: 200 OK {username, roles} + Set-Cookie: JSESSIONID
    
    Note over C,SS: Subsequent requests include JSESSIONID
    C->>AC: GET /api/v1/donations<br/>Cookie: JSESSIONID=xyz
    SS-->>AC: Retrieve SecurityContext → validate role
```

---

## 8. Interaction Flows (Sequence)

### Create Donor

```mermaid
sequenceDiagram
    actor C as Client (OPERATOR/TREASURER)
    participant DC as DonorController
    participant DS as DonorService
    participant VAL as DniNieValidator
    participant DR as DonorRepository
    participant DB as PostgreSQL

    C->>DC: POST /api/v1/donors {fullName, dniNie, email, ...}
    DC->>VAL: Validate @ValidDniNie (format + check digit)
    VAL-->>DC: OK / 400 if invalid
    DC->>DS: createDonor(CreateDonorRequest)
    DS->>DR: existsByDniNie(dniNie)
    DR->>DB: SELECT COUNT(*) FROM donors WHERE dni_nie=?
    DB-->>DR: 0 / 1
    alt dni_nie already exists
        DS-->>DC: throw IllegalStateException
        DC-->>C: 409 Conflict
    else dni_nie available
        DS->>DR: save(Donor)
        DR->>DB: INSERT INTO donors ...
        DB-->>DR: Donor with generated id
        DR-->>DS: Donor
        DS-->>DC: Donor
        DC-->>C: 201 Created {DonorResponse}
    end
```

### Generate Donation Report by Type

```mermaid
sequenceDiagram
    actor C as Client (TREASURER/PASTOR)
    participant RC as ReportController
    participant RS as ReportService
    participant DOR as DonationRepository
    participant DB as PostgreSQL

    C->>RC: GET /api/v1/reports/donations?from=2024-01-01&to=2024-12-31
    RC->>RS: donationSummary(from, to)
    RS->>DOR: sumByTypeAndDateBetween(from, to)
    DOR->>DB: SELECT donation_type, SUM(amount)<br/>FROM donations<br/>WHERE donation_date BETWEEN ? AND ?<br/>GROUP BY donation_type
    DB-->>DOR: List[(type, sum)]
    DOR-->>RS: List[Array<Any>]
    RS->>DOR: sumAmountByDateBetween(from, to)
    DOR->>DB: SELECT SUM(amount) FROM donations<br/>WHERE donation_date BETWEEN ? AND ?
    DB-->>DOR: BigDecimal
    DOR-->>RS: grandTotal
    RS-->>RC: DonationSummaryResponse
    RC-->>C: 200 OK {from, to, totalsByType, grandTotal}
```

### Change Own Password

```mermaid
sequenceDiagram
    actor U as Authenticated user
    participant UC as UserController
    participant US as UserService
    participant PE as PasswordEncoder (BCrypt)
    participant UR as UserRepository
    participant DB as PostgreSQL

    U->>UC: PUT /api/v1/users/me/password<br/>{currentPassword, newPassword}
    UC->>US: changeOwnPassword(username, currentPassword, newPassword)
    US->>UR: findByUsername(username)
    UR->>DB: SELECT * FROM users WHERE username=?
    DB-->>UR: User
    US->>PE: matches(currentPassword, user.password)
    PE-->>US: true / false
    alt current password incorrect
        US-->>UC: throw IllegalArgumentException
        UC-->>U: 400 Bad Request
    else current password correct
        US->>PE: encode(newPassword)
        PE-->>US: BCrypt hash
        US->>UR: save(user with new hash)
        UR->>DB: UPDATE users SET password=? WHERE id=?
        DB-->>UR: OK
        UC-->>U: 204 No Content
    end
```

---

## 9. Package Structure

```
com.donations/
│
├── DonationsApplication.kt              ← Entry point
│
├── user/                                ← Domain: Users
│   ├── User.kt                          Entity
│   ├── Role.kt                          Enum
│   ├── UserRepository.kt               JPA Repository
│   ├── UserService.kt                  Service (CRUD + passwords)
│   ├── UserController.kt               REST Controller
│   ├── UserDtos.kt                     Request / Response DTOs
│   ├── AuthController.kt               Login endpoint
│   └── AppUserDetailsService.kt        Spring Security bridge
│
├── donor/                               ← Domain: Donors
│   ├── Donor.kt                         Entity
│   ├── DonorRepository.kt              JPA Repository
│   ├── DonorService.kt                 Service
│   ├── DonorController.kt              REST Controller
│   ├── DonorDtos.kt                    DTOs
│   └── validation/
│       ├── ValidDniNie.kt              Annotation
│       └── DniNieValidator.kt          DNI/NIE logic (modulo 23)
│
├── donation/                            ← Domain: Donations
│   ├── Donation.kt                      Entity
│   ├── DonationType.kt                 Enum
│   ├── PaymentMethod.kt                Enum (shared with Expense)
│   ├── DonationRepository.kt           JPA + @Query aggregations
│   ├── DonationService.kt             Service (duplicate detection)
│   ├── DonationController.kt           REST Controller
│   └── DonationDtos.kt                DTOs (incl. DonationCreateResponse)
│
├── expense/                             ← Domain: Expenses
│   ├── Expense.kt                       Entity
│   ├── ExpenseCategory.kt              Enum
│   ├── ExpenseRepository.kt            JPA + @Query aggregations
│   ├── ExpenseService.kt               Service
│   ├── ExpenseController.kt            REST Controller
│   └── ExpenseDtos.kt                 DTOs
│
├── report/                              ← Domain: Reports
│   ├── ReportService.kt               Service (read-only, no own entity)
│   ├── ReportController.kt             REST Controller
│   └── ReportDtos.kt                  Response DTOs
│
└── infrastructure/
    ├── config/
    │   ├── SecurityConfig.kt           Auth, CORS, sessions, BCrypt
    │   └── OpenApiConfig.kt            Swagger / OpenAPI 3
    ├── audit/
    │   ├── AuditableEntity.kt          Base entity (id + audit fields)
    │   └── AuditorAwareConfig.kt       Reads SecurityContext → createdBy
    └── error/
        ├── GlobalExceptionHandler.kt   Centralized @RestControllerAdvice
        ├── NotFoundException.kt        Custom 404 exception
        └── ErrorResponse.kt           JSON error structure

src/main/resources/
├── application.yaml                     Config + dev/prod profiles
└── db/migration/
    ├── V1__create_schema_baseline.sql
    ├── V2__create_users_and_roles.sql
    ├── V3__seed_admin_user.sql
    ├── V4__create_donors.sql
    ├── V5__create_donations.sql
    └── V6__create_expenses.sql
```

---

## Design Decisions Summary

| Decision | Implementation | Reason |
|----------|---------------|--------|
| Authentication | HTTP session (JSESSIONID) | Simple for an API consumed by a single frontend |
| Authorization | `@PreAuthorize` per method | Granular, co-located with the endpoint |
| Auditing | `AuditableEntity` + `AuditorAwareConfig` | Traceability of who created/modified each record |
| Duplicates | Detection in `DonationService` + `confirmDuplicate` flag | Prevent double-entry errors without blocking the operator |
| DNI/NIE validation | Custom validator (modulo 23) | Requirement of the Spanish church context |
| Migrations | Flyway V1–V6 | Version-controlled schema, reproducible |
| Reports | `ReportService` with no own entity | Pure aggregations, no persisted state |
| Profiles | `dev` (CORS + Swagger) / `prod` (no Swagger) | Swagger not exposed in production |
