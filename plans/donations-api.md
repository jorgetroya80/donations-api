# Plan: Church Donations and Expenditure Management API

> Source PRD: [jorgetroya80/donations-api#1](https://github.com/jorgetroya80/donations-api/issues/1) / `PRD.md`

## Architectural decisions

Durable decisions across all phases:

- **Routes**: All endpoints under `/api/v1/`. Auth at `/api/v1/login`, `/api/v1/logout`. Resources: `/api/v1/users`, `/api/v1/donors`, `/api/v1/donations`, `/api/v1/expenses`. Reports: `/api/v1/reports/donations`, `/api/v1/reports/expenses`, `/api/v1/reports/balance`, `/api/v1/reports/donors/{id}/statement`. Password change: `/api/v1/users/me/password`.
- **Schema**: PostgreSQL + Flyway migrations. Monetary amounts `DECIMAL(10,2)` → `BigDecimal`. Enums stored as strings. All entities have `created_by`, `created_at`, `updated_by`, `updated_at` audit columns.
- **Key models**: User (many-to-many with Role), Donor, Donation, Expense. Roles: `ADMIN`, `TREASURER`, `PASTOR`, `OPERATOR`.
- **Auth**: Session-based via Spring Security. Passwords bcrypt-hashed. No JWT, no OAuth.
- **Pagination**: Page-number via Spring Data `Pageable` on all list endpoints.
- **Error format**: Custom JSON error body (`status`, `error`, `message`, `fields`, `timestamp`) via `@RestControllerAdvice`.
- **No deletes**: No hard deletes. Donors/users soft-deactivated. Donations/expenses edit-only.
- **Currency**: Euros only, no multi-currency.

---

## Phase 1: Foundation — Audit, Error Handling, and Database Infrastructure

**User stories**: 31

### What to build

Enable Flyway, set up shared infrastructure all later phases need. Create base auditable entity (or JPA listener) auto-populating `created_by`, `created_at`, `updated_by`, `updated_at` from Spring Security context. Build global `@RestControllerAdvice` exception handler producing custom error response — handles validation errors (field-level detail), auth errors, authorization errors, generic exceptions. Configure Springdoc OpenAPI with v1 metadata.

### Acceptance criteria

- [ ] Flyway enabled, runs migrations on startup against Docker Compose PostgreSQL
- [ ] Base audit mechanism populates `created_by`, `created_at`, `updated_by`, `updated_at` automatically — `created_by`/`created_at` set on insert only, `updated_by`/`updated_at` on every update
- [ ] Global exception handler returns custom error JSON for validation errors (with `fields` map), 401, 403, unexpected errors
- [ ] Springdoc OpenAPI spec accessible, reflects v1 versioning
- [ ] App starts and connects to PostgreSQL

---

## Phase 2: Authentication

**User stories**: 6, 7

### What to build

Configure Spring Security for session-based auth. Create `users` and `user_roles` tables via Flyway. Build User and Role entities. Implement `UserDetailsService` loading users from PostgreSQL. Create `POST /api/v1/login` (accepts username/password, creates session) and `POST /api/v1/logout` (invalidates session). Seed initial Admin user via Flyway with bcrypt-hashed password. All other endpoints require auth (401 if no session).

### Acceptance criteria

- [ ] `POST /api/v1/login` valid credentials → 200 + session cookie
- [ ] `POST /api/v1/login` invalid credentials → 401
- [ ] `POST /api/v1/logout` invalidates session
- [ ] Unauthenticated requests to protected endpoints → 401
- [ ] Initial Admin user exists after Flyway migration (seeded bcrypt-hashed)
- [ ] User entity supports multiple roles (many-to-many with roles table)

---

## Phase 3: User Management

**User stories**: 1, 2, 3, 4, 5

### What to build

User management endpoints, Admin-only. `GET /api/v1/users` lists all. `POST /api/v1/users` creates user with roles. `PUT /api/v1/users/{id}` updates user (deactivation + password reset). `PUT /api/v1/users/me/password` lets any authenticated user change own password (requires current + new). Role auth: only Admin manages users; password change open to any authenticated user.

### Acceptance criteria

- [ ] Admin can create user with one or more roles
- [ ] Admin can deactivate user (soft disable, not delete)
- [ ] Admin can reset user password
- [ ] Admin can assign multiple roles
- [ ] Non-Admin roles get 403 on user management endpoints
- [ ] Any authenticated user can change own password via `/api/v1/users/me/password`
- [ ] Password change fails 400 if current password wrong
- [ ] Deactivated users cannot log in
- [ ] Security Module tests pass: login, logout, 401, 403, password change

---

## Phase 4: Donor Management

**User stories**: 15, 16, 26, 29

### What to build

Create `donors` table via Flyway. Donor entity fields: full name (required), DNI/NIE (required, Spanish format validated), email, phone, address (optional), active flag. CRUD endpoints: `POST /api/v1/donors` creates, `GET /api/v1/donors` lists paginated, `GET /api/v1/donors/{id}` retrieves, `PUT /api/v1/donors/{id}` updates (including deactivation). No DELETE. Roles: Operator + Treasurer access; Pastor + Admin get 403.

### Acceptance criteria

- [ ] Create donor with valid name + DNI/NIE succeeds
- [ ] DNI/NIE validation rejects invalid Spanish ID formats
- [ ] Donor list returns paginated results
- [ ] Donor deactivated via PUT (active → false)
- [ ] No DELETE endpoint (returns 405 or 404)
- [ ] Operator + Treasurer access; Pastor + Admin get 403
- [ ] Audit columns populated on create and update

---

## Phase 5: Donation Recording

**User stories**: 8, 9, 10, 11, 12, 27

### What to build

Create `donations` table via Flyway, FK to `donors` (nullable for anonymous). Donation entity: amount (`DECIMAL(10,2)`, positive, non-zero), date (no future), type enum (`TITHE`, `OFFERING`, `SPECIAL_OFFERING`, `OTHER`), payment method (`CASH`, `BANK_TRANSFER`), donor ref (nullable). `POST /api/v1/donations` with duplicate detection: non-anonymous donation matching donor + amount + date + type → return warning but still allow save (non-blocking — client can include confirmation flag). `GET /api/v1/donations` paginated + date range filter. `GET /api/v1/donations/{id}` and `PUT /api/v1/donations/{id}`. No DELETE. Roles: Operator + Treasurer create/edit.

### Acceptance criteria

- [ ] Create donation with valid data succeeds
- [ ] Zero/negative amounts rejected
- [ ] Future dates rejected
- [ ] Anonymous donations (no donor) accepted
- [ ] Duplicate detection warns when same donor + amount + date + type exists
- [ ] Duplicate warning non-blocking (saves when confirmed)
- [ ] Duplicate detection skips anonymous donations
- [ ] PUT edit works, updates audit fields
- [ ] No DELETE endpoint
- [ ] List supports pagination + date range filter
- [ ] Operator + Treasurer create/edit; Pastor + Admin get 403
- [ ] Donation Module integration tests cover all above

---

## Phase 6: Expense Recording

**User stories**: 13, 14, 17, 28

### What to build

Create `expenses` table via Flyway. Expense entity: amount (`DECIMAL(10,2)`, positive, non-zero), date (no future), category enum (`RENT`, `UTILITIES`, `SALARIES`, `SUPPLIES`, `MISSIONS`, `MAINTENANCE`, `OTHER`), description (required), vendor/payee (optional), payment method (`CASH`, `BANK_TRANSFER`). `POST /api/v1/expenses`, `GET /api/v1/expenses` (paginated, date range filter), `GET /api/v1/expenses/{id}`, `PUT /api/v1/expenses/{id}`. No DELETE. No approval workflow. Roles: Operator + Treasurer create/edit.

### Acceptance criteria

- [ ] Create expense with valid data succeeds
- [ ] Zero/negative amounts rejected
- [ ] Future dates rejected
- [ ] List supports pagination + date range filter
- [ ] PUT edit works, updates audit fields
- [ ] No DELETE endpoint
- [ ] Operator + Treasurer create/edit; Pastor + Admin get 403
- [ ] Audit columns populated correctly

---

## Phase 7: Financial Reports

**User stories**: 18, 19, 20, 21, 22, 23, 24, 25, 30

### What to build

Four read-only report endpoints, parameterized by `from`/`to` date range (default: current calendar year Jan 1 – Dec 31):

1. `GET /api/v1/reports/donations` — total donations grouped by type
2. `GET /api/v1/reports/expenses` — total expenses grouped by category
3. `GET /api/v1/reports/balance` — total income vs total expenses + net balance
4. `GET /api/v1/reports/donors/{id}/statement` — all contributions by specific donor (basis for certificado de donaciones)

Roles: Treasurer + Pastor only. Pastor gets read-only access to everything through these endpoints.

### Acceptance criteria

- [ ] Donation summary returns correct totals by type for date range
- [ ] Expense summary returns correct totals by category for date range
- [ ] Balance report returns total income, total expenses, net balance
- [ ] Donor statement returns all donations by specific donor for period
- [ ] Date range defaults to current calendar year when omitted
- [ ] Treasurer + Pastor access; Operator + Admin get 403
- [ ] Light integration tests verify response structure + role enforcement

---

## Phase 8: Springdoc/OpenAPI and Production Hardening

**User stories**: 32

### What to build

Configure Springdoc with Spring profile-based access. `dev` profile: Swagger UI accessible without auth. `prod` profile: Swagger UI secured or disabled. Ensure OpenAPI spec metadata has API version (v1), title, description. Verify all endpoints documented correctly in generated spec.

### Acceptance criteria

- [ ] Swagger UI accessible without auth in `dev` profile
- [ ] Swagger UI secured or disabled in `prod` profile
- [ ] OpenAPI spec has correct v1 version, API title, description
- [ ] All endpoints appear in generated spec with correct request/response schemas