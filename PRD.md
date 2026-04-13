# PRD: Church Donations and Expenditure Management API

## Problem Statement

A church needs a centralized system to manage its financial operations. Currently, tracking donations (tithes, offerings), recording expenses, and generating financial reports for compliance (certificado de donaciones) are manual processes prone to error. The treasurer, pastor, and data entry operators need a shared system with appropriate access controls — the operator records transactions, the treasurer oversees finances, and the pastor reviews donor contributions for pastoral and tax purposes. There is no audit trail for who entered or modified records, and producing year-end donor statements for Spanish tax compliance is time-consuming.

## Solution

A REST API built with Kotlin/Spring Boot backed by PostgreSQL that provides:

- **Role-based access** for four roles (Admin, Treasurer, Pastor, Operator) with session-based authentication
- **Donor management** with DNI/NIE tracking for Spanish tax receipts
- **Donation recording** with duplicate detection warnings and full validation (positive amounts, no future dates)
- **Expense recording** with categorized spending
- **Financial reports** including donation summaries, expense summaries, income-vs-expense balance, and individual donor statements
- **Audit columns** on all entities tracking who created and last modified each record

## User Stories

1. As an Admin, I want to create user accounts with assigned roles, so that the church team can access the system with appropriate permissions.
2. As an Admin, I want to deactivate user accounts, so that former team members lose access without deleting their audit trail.
3. As an Admin, I want to assign multiple roles to a single user, so that the same person can serve as both Admin and Treasurer.
4. As an Admin, I want to reset a user's password, so that I can help team members who are locked out.
5. As any authenticated user, I want to change my own password, so that I can maintain my account security.
6. As any authenticated user, I want to log in with my username and password, so that I can access the system.
7. As any authenticated user, I want to log out, so that my session is terminated securely.
8. As an Operator, I want to record a donation with donor, amount, date, type, and payment method, so that the church has an accurate financial record.
9. As an Operator, I want to record an anonymous donation (no donor linked), so that unattributed offerings are still tracked.
10. As an Operator, I want to be warned when I enter a donation that matches an existing one (same donor, amount, date, and type), so that I can avoid accidental duplicates.
11. As an Operator, I want to proceed with saving a donation despite a duplicate warning, so that legitimate repeated donations are not blocked.
12. As an Operator, I want to edit a previously recorded donation, so that I can correct data entry mistakes.
13. As an Operator, I want to record an expense with amount, date, category, description, and payment method, so that church spending is tracked.
14. As an Operator, I want to edit a previously recorded expense, so that I can correct mistakes.
15. As an Operator, I want to register new donors with their full name and DNI/NIE, so that their contributions can be tracked for tax purposes.
16. As an Operator, I want to update donor contact information (email, phone, address), so that records stay current.
17. As a Treasurer, I want to perform all actions an Operator can, so that I can also handle data entry when needed.
18. As a Treasurer, I want to view a donation summary grouped by type for a given date range, so that I can see total tithes, offerings, and other income.
19. As a Treasurer, I want to view an expense summary grouped by category for a given date range, so that I can see where the church is spending.
20. As a Treasurer, I want to view an income-vs-expenses balance for a given date range, so that I can assess the church's financial health.
21. As a Treasurer, I want to generate an individual donor statement for a date range, so that I can produce the annual certificado de donaciones for tax compliance.
22. As a Treasurer, I want to view individual donor contribution history, so that I can verify records before issuing tax receipts.
23. As a Pastor, I want to view individual donor contributions with amounts, so that I have context for pastoral care.
24. As a Pastor, I want to view donation and expense reports, so that I can stay informed about the church's financial position.
25. As a Pastor, I want read-only access (no ability to create or modify financial records), so that separation of duties is maintained.
26. As an Operator, I want to deactivate a donor (not delete), so that historical donation data is preserved when someone leaves the church.
27. As an Operator, I want to list donations with pagination and filtering by date range, so that I can find specific records efficiently.
28. As an Operator, I want to list expenses with pagination and filtering by date range, so that I can review spending records.
29. As an Operator, I want to list donors with pagination, so that I can browse and search the donor directory.
30. As a Treasurer, I want report date ranges to default to the current calendar year, so that the most common reporting period is convenient.
31. As any user, I want clear validation error messages indicating which fields are invalid and why, so that I can fix my input.
32. As a developer, I want Swagger UI available in development, so that I can explore and test the API interactively.

## Implementation Decisions

### Architecture
- Standard layered architecture: Controller → Service → Repository per domain module
- All endpoints versioned under `/api/v1/`
- Page-number pagination via Spring Data `Pageable` on all list endpoints
- Global error handling via `@RestControllerAdvice` with custom error response structure including field-level validation errors

### Modules
1. **Security Module** — Spring Security configuration with session-based auth, `UserDetailsService` backed by PostgreSQL, role-based method/endpoint authorization, login/logout endpoints
2. **User Management Module** — User CRUD restricted to Admin role, password change endpoint for authenticated users, many-to-many user-role relationship
3. **Donor Module** — Donor CRUD with DNI/NIE validation (Spanish format), soft delete via active/inactive flag, no hard deletes
4. **Donation Module** — Create and edit (no delete), validates positive non-zero amounts, rejects future dates, duplicate detection (same donor + amount + date + type) returning a non-blocking warning, supports anonymous donations via nullable donor reference
5. **Expense Module** — Create and edit (no delete), validates positive non-zero amounts, rejects future dates, extensible category enum designed so adding new values does not require breaking changes
6. **Reports Module** — Read-only endpoints for donation summary by type, expense summary by category, income-vs-expenses balance, and individual donor statements, all parameterized by date range
7. **Error Handling Module** — `@RestControllerAdvice` producing a consistent JSON error body with status, error label, message, optional field-level errors map, and timestamp
8. **Audit Infrastructure** — JPA entity listener or `@MappedSuperclass` that auto-populates `created_by`, `created_at`, `updated_by`, `updated_at` from the Spring Security context

### Database
- PostgreSQL with Flyway migrations
- Monetary amounts stored as `DECIMAL(10,2)` mapped to `BigDecimal` in Kotlin
- Euros only, no multi-currency support
- Initial Admin user seeded via Flyway migration with bcrypt-hashed password
- Enums stored as strings in the database for readability and safe extensibility

### API Endpoints
```
POST   /api/v1/login
POST   /api/v1/logout

GET    /api/v1/users
POST   /api/v1/users
PUT    /api/v1/users/{id}
PUT    /api/v1/users/me/password

GET    /api/v1/donors
POST   /api/v1/donors
GET    /api/v1/donors/{id}
PUT    /api/v1/donors/{id}

GET    /api/v1/donations
POST   /api/v1/donations
GET    /api/v1/donations/{id}
PUT    /api/v1/donations/{id}

GET    /api/v1/expenses
POST   /api/v1/expenses
GET    /api/v1/expenses/{id}
PUT    /api/v1/expenses/{id}

GET    /api/v1/reports/donations?from=...&to=...
GET    /api/v1/reports/expenses?from=...&to=...
GET    /api/v1/reports/balance?from=...&to=...
GET    /api/v1/reports/donors/{id}/statement?from=...&to=...
```

### Role-Permission Matrix

| Action | Admin | Treasurer | Pastor | Operator |
|---|---|---|---|---|
| Manage users | Yes | No | No | No |
| Record/edit donations | No | Yes | No | Yes |
| Record/edit expenses | No | Yes | No | Yes |
| View/edit donor info | No | Yes | No | Yes |
| View donor contributions | No | Yes | Yes | No |
| Generate financial reports | No | Yes | Yes | No |

### Springdoc/OpenAPI
- Swagger UI accessible without authentication in development (Spring profile)
- Secured or disabled in production (Spring profile)
- OpenAPI spec metadata reflects API version v1

## Testing Decisions

Tests should verify external behavior through the API surface, not internal implementation details. Use real HTTP requests against the running application context with a real PostgreSQL database via Testcontainers — no mocking the database layer.

### Donation Module (thorough coverage)
- Validate that creating a donation with valid data returns success and persists correctly
- Validate rejection of zero and negative amounts
- Validate rejection of future dates
- Validate that anonymous donations (no donor) are accepted
- Validate duplicate detection: creating a donation with same donor + amount + date + type returns a warning
- Validate that duplicate warning is non-blocking (donation is still saved when confirmed)
- Validate that duplicate detection does not apply to anonymous donations
- Validate that editing a donation works and updates audit fields
- Validate that donations cannot be deleted (no DELETE endpoint, returns 405 or 404)
- Validate pagination and date range filtering on the list endpoint
- Validate role-based access: Operator and Treasurer can create/edit, Pastor and Admin cannot

### Security Module (thorough coverage)
- Validate successful login with correct credentials returns a session
- Validate login failure with wrong credentials returns 401
- Validate that unauthenticated requests to protected endpoints return 401
- Validate that unauthorized role access returns 403 (e.g., Operator trying to access reports, Pastor trying to create a donation)
- Validate session logout invalidates the session
- Validate password change with correct current password succeeds
- Validate password change with wrong current password fails
- Validate Admin can create users with assigned roles
- Validate non-Admin roles cannot access user management endpoints

### Reports Module (light coverage)
- Validate that each report endpoint returns data in the expected structure for a given date range
- Validate that report endpoints enforce role-based access (Treasurer and Pastor only)

### Prior Art
The codebase already includes a Testcontainers configuration (`TestcontainersConfiguration.kt`) and a Spring Boot test class (`DonationsApplicationTests.kt`) that loads the full application context. New tests should follow this pattern — `@Import(TestcontainersConfiguration::class)` with `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `TestRestTemplate` or `MockMvc` for HTTP assertions.

## Out of Scope

- **PDF generation** for tax receipts (certificado de donaciones) — deferred to a future phase
- **Top donor ranking report** — may be added later
- **Email notifications** or digital receipt delivery
- **Multi-currency support** — euros only
- **Approval workflows** for expenses
- **Hard deletion** of any records (donors, donations, expenses, users)
- **Self-registration** or email-based password reset
- **Frontend / UI** — this PRD covers the API only
- **Forced password change on first login**

## Further Notes

- The church is based in Spain; donor national IDs follow DNI/NIE format
- Expense and donation type enums should be designed so that adding new values is a simple code + migration change with no breaking impact on existing data
- Calendar year (Jan–Dec) is the fiscal period for report defaults
- The Flyway seed migration for the initial Admin user should use a bcrypt-hashed password that is documented for the deployment team to change immediately after first setup
