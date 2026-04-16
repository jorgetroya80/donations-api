# Plan: Church Donations and Expenditure Management API

> Source PRD: [jorgetroya80/donations-api#1](https://github.com/jorgetroya80/donations-api/issues/1) / `PRD.md`

## Architectural decisions

Durable decisions that apply across all phases:

- **Routes**: All endpoints under `/api/v1/`. Auth at `/api/v1/login`, `/api/v1/logout`. Resources: `/api/v1/users`, `/api/v1/donors`, `/api/v1/donations`, `/api/v1/expenses`. Reports: `/api/v1/reports/donations`, `/api/v1/reports/expenses`, `/api/v1/reports/balance`, `/api/v1/reports/donors/{id}/statement`. Password change: `/api/v1/users/me/password`.
- **Schema**: PostgreSQL with Flyway migrations. Monetary amounts as `DECIMAL(10,2)` → `BigDecimal`. Enums stored as strings. All entities have `created_by`, `created_at`, `updated_by`, `updated_at` audit columns.
- **Key models**: User (many-to-many with Role), Donor, Donation, Expense. Roles: `ADMIN`, `TREASURER`, `PASTOR`, `OPERATOR`.
- **Auth**: Session-based authentication via Spring Security. Passwords bcrypt-hashed. No JWT, no OAuth.
- **Pagination**: Page-number pagination via Spring Data `Pageable` on all list endpoints.
- **Error format**: Custom JSON error body (`status`, `error`, `message`, `fields`, `timestamp`) via `@RestControllerAdvice`.
- **No deletes**: No hard deletes anywhere. Donors and users are soft-deactivated. Donations and expenses are edit-only.
- **Currency**: Euros only, no multi-currency.

---

## Phase 1: Foundation — Audit, Error Handling, and Database Infrastructure

**User stories**: 31

### What to build

Enable Flyway and set up the shared infrastructure that all subsequent phases depend on. Create a base auditable entity (or JPA listener) that auto-populates `created_by`, `created_at`, `updated_by`, `updated_at` from the Spring Security context. Build the global `@RestControllerAdvice` exception handler that produces the custom error response format — handling validation errors (with field-level detail), authentication errors, authorization errors, and generic exceptions. Configure Springdoc OpenAPI with v1 metadata.

### Acceptance criteria

- [ ] Flyway is enabled and runs migrations on startup against the Docker Compose PostgreSQL instance
- [ ] A base audit mechanism exists that populates `created_by`, `created_at`, `updated_by`, `updated_at` automatically — `created_by`/`created_at` set on insert only, `updated_by`/`updated_at` set on every update
- [ ] Global exception handler returns the custom error JSON structure for validation errors (with `fields` map), 401, 403, and unexpected errors
- [ ] Springdoc OpenAPI spec is accessible and reflects v1 versioning in its metadata
- [ ] Application starts and connects to PostgreSQL successfully

---

## Phase 2: Authentication

**User stories**: 6, 7

### What to build

Configure Spring Security for session-based authentication. Create the `users` and `user_roles` tables via Flyway migration. Build the User and Role entities. Implement a `UserDetailsService` that loads users from PostgreSQL. Create `POST /api/v1/login` (accepts username/password, creates a session) and `POST /api/v1/logout` (invalidates the session). Seed an initial Admin user via Flyway migration with a bcrypt-hashed password. All other endpoints should require authentication (return 401 if no session).

### Acceptance criteria

- [ ] `POST /api/v1/login` with valid credentials returns 200 and a session cookie
- [ ] `POST /api/v1/login` with invalid credentials returns 401
- [ ] `POST /api/v1/logout` invalidates the session
- [ ] Unauthenticated requests to any protected endpoint return 401
- [ ] An initial Admin user exists after Flyway migration runs (seeded with bcrypt-hashed password)
- [ ] User entity supports multiple roles (many-to-many with roles table)

---

## Phase 3: User Management

**User stories**: 1, 2, 3, 4, 5

### What to build

Build user management endpoints restricted to the Admin role. `GET /api/v1/users` lists all users. `POST /api/v1/users` creates a user with assigned roles. `PUT /api/v1/users/{id}` updates a user (including deactivation and password reset). `PUT /api/v1/users/me/password` allows any authenticated user to change their own password (requires current password + new password). Role-based authorization: only Admin can access user management endpoints; password change is available to any authenticated user.

### Acceptance criteria

- [ ] Admin can create a new user with one or more roles
- [ ] Admin can deactivate a user (soft disable, not delete)
- [ ] Admin can reset a user's password
- [ ] Admin can assign multiple roles to a user
- [ ] Non-Admin roles receive 403 on user management endpoints
- [ ] Any authenticated user can change their own password via `/api/v1/users/me/password`
- [ ] Password change fails with 400 if current password is incorrect
- [ ] Deactivated users cannot log in
- [ ] Security Module tests pass: login, logout, 401, 403, password change scenarios

---

## Phase 4: Donor Management

**User stories**: 15, 16, 26, 29

### What to build

Create the `donors` table via Flyway migration. Build the Donor entity with fields: full name (required), DNI/NIE (required, validated for Spanish format), email, phone, address (all optional), and active flag. Implement CRUD endpoints: `POST /api/v1/donors` creates a donor, `GET /api/v1/donors` lists donors with pagination, `GET /api/v1/donors/{id}` retrieves a single donor, `PUT /api/v1/donors/{id}` updates a donor (including deactivation). No DELETE endpoint. Role enforcement: Operator and Treasurer can access; Pastor and Admin cannot.

### Acceptance criteria

- [ ] Create a donor with valid full name and DNI/NIE succeeds
- [ ] DNI/NIE validation rejects invalid Spanish ID formats
- [ ] Donor list endpoint returns paginated results
- [ ] Donor can be deactivated via PUT (active flag set to false)
- [ ] No DELETE endpoint exists (returns 405 or 404)
- [ ] Operator and Treasurer can access donor endpoints; Pastor and Admin receive 403
- [ ] Audit columns are populated correctly on create and update

---

## Phase 5: Donation Recording

**User stories**: 8, 9, 10, 11, 12, 27

### What to build

Create the `donations` table via Flyway migration with a foreign key to `donors` (nullable for anonymous). Build the Donation entity with fields: amount (`DECIMAL(10,2)`, positive, non-zero), date (no future dates), type enum (`TITHE`, `OFFERING`, `SPECIAL_OFFERING`, `OTHER`), payment method (`CASH`, `BANK_TRANSFER`), and donor reference (nullable). Implement `POST /api/v1/donations` with duplicate detection: when a non-anonymous donation matches an existing one on donor + amount + date + type, return a warning in the response but still allow saving (non-blocking — the client can include a confirmation flag to acknowledge the duplicate). `GET /api/v1/donations` with pagination and date range filtering. `GET /api/v1/donations/{id}` and `PUT /api/v1/donations/{id}` for retrieval and editing. No DELETE. Role enforcement: Operator and Treasurer can create/edit.

### Acceptance criteria

- [ ] Create a donation with valid data succeeds and persists
- [ ] Zero and negative amounts are rejected with validation error
- [ ] Future dates are rejected with validation error
- [ ] Anonymous donations (no donor) are accepted
- [ ] Duplicate detection returns a warning when same donor + amount + date + type exists
- [ ] Duplicate warning is non-blocking (donation saves when confirmed)
- [ ] Duplicate detection does not apply to anonymous donations
- [ ] Edit via PUT works and updates audit fields
- [ ] No DELETE endpoint exists
- [ ] List endpoint supports pagination and date range filtering
- [ ] Operator and Treasurer can create/edit; Pastor and Admin receive 403
- [ ] Donation Module integration tests pass covering all scenarios above

---

## Phase 6: Expense Recording

**User stories**: 13, 14, 17, 28

### What to build

Create the `expenses` table via Flyway migration. Build the Expense entity with fields: amount (`DECIMAL(10,2)`, positive, non-zero), date (no future dates), category enum (`RENT`, `UTILITIES`, `SALARIES`, `SUPPLIES`, `MISSIONS`, `MAINTENANCE`, `OTHER`), description (required), vendor/payee (optional), payment method (`CASH`, `BANK_TRANSFER`). Implement `POST /api/v1/expenses`, `GET /api/v1/expenses` (paginated, date range filtering), `GET /api/v1/expenses/{id}`, `PUT /api/v1/expenses/{id}`. No DELETE. No approval workflow. Role enforcement: Operator and Treasurer can create/edit.

### Acceptance criteria

- [ ] Create an expense with valid data succeeds
- [ ] Zero and negative amounts are rejected
- [ ] Future dates are rejected
- [ ] List endpoint supports pagination and date range filtering
- [ ] Edit via PUT works and updates audit fields
- [ ] No DELETE endpoint exists
- [ ] Operator and Treasurer can create/edit; Pastor and Admin receive 403
- [ ] Audit columns are populated correctly

---

## Phase 7: Financial Reports

**User stories**: 18, 19, 20, 21, 22, 23, 24, 25, 30

### What to build

Four read-only report endpoints, all parameterized by `from` and `to` date range (defaulting to current calendar year Jan 1 – Dec 31):

1. `GET /api/v1/reports/donations` — total donations grouped by type for the period
2. `GET /api/v1/reports/expenses` — total expenses grouped by category for the period
3. `GET /api/v1/reports/balance` — total income vs total expenses and net balance for the period
4. `GET /api/v1/reports/donors/{id}/statement` — all contributions by a specific donor for the period (basis for certificado de donaciones)

Role enforcement: Treasurer and Pastor only. Pastor has read-only access to everything in the system through these endpoints.

### Acceptance criteria

- [ ] Donation summary returns correct totals grouped by type for a given date range
- [ ] Expense summary returns correct totals grouped by category for a given date range
- [ ] Balance report returns total income, total expenses, and net balance
- [ ] Donor statement returns all donations by a specific donor for the period
- [ ] Date range defaults to current calendar year when not provided
- [ ] Treasurer and Pastor can access; Operator and Admin receive 403
- [ ] Light integration tests verify response structure and role enforcement

---

## Phase 8: Springdoc/OpenAPI and Production Hardening

**User stories**: 32

### What to build

Configure Springdoc with Spring profile-based access control. In the `dev` profile, Swagger UI is accessible without authentication. In the `prod` profile, Swagger UI is either secured behind authentication or disabled entirely. Ensure the OpenAPI spec metadata reflects the API version (v1), title, and description. Review and verify that all endpoints are correctly documented in the generated spec.

### Acceptance criteria

- [ ] Swagger UI is accessible without authentication when running with the `dev` profile
- [ ] Swagger UI is secured or disabled when running with the `prod` profile
- [ ] OpenAPI spec includes correct v1 version, API title, and description
- [ ] All endpoints appear in the generated OpenAPI spec with correct request/response schemas
