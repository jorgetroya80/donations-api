# PRD: Church Donations and Expenditure Management API

## Problem Statement

Church needs centralized system for financial operations. Tracking donations (tithes, offerings), recording expenses, generating financial reports for compliance (certificado de donaciones) — all manual, error-prone. Treasurer, pastor, data entry operators need shared system with access controls — operator records transactions, treasurer oversees finances, pastor reviews donor contributions for pastoral and tax purposes. No audit trail for who entered or modified records. Producing year-end donor statements for Spanish tax compliance is slow.

## Solution

REST API built with Kotlin/Spring Boot backed by PostgreSQL providing:

- **Role-based access** for four roles (Admin, Treasurer, Pastor, Operator) with session-based authentication
- **Donor management** with DNI/NIE tracking for Spanish tax receipts
- **Donation recording** with duplicate detection warnings and full validation (positive amounts, no future dates)
- **Expense recording** with categorized spending
- **Financial reports** including donation summaries, expense summaries, income-vs-expense balance, individual donor statements
- **Audit columns** on all entities tracking who created and last modified each record

## User Stories

1. As Admin, create user accounts with assigned roles so church team accesses system with appropriate permissions.
2. As Admin, deactivate user accounts so former team members lose access without deleting audit trail.
3. As Admin, assign multiple roles to single user so same person can serve as both Admin and Treasurer.
4. As Admin, reset user's password to help locked-out team members.
5. As authenticated user, change own password to maintain account security.
6. As authenticated user, log in with username and password to access system.
7. As authenticated user, log out to terminate session securely.
8. As Operator, record donation with donor, amount, date, type, payment method for accurate financial record.
9. As Operator, record anonymous donation (no donor linked) so unattributed offerings still tracked.
10. As Operator, get warned when entering donation matching existing one (same donor, amount, date, type) to avoid accidental duplicates.
11. As Operator, proceed with saving donation despite duplicate warning so legitimate repeated donations not blocked.
12. As Operator, edit previously recorded donation to correct data entry mistakes.
13. As Operator, record expense with amount, date, category, description, payment method so church spending tracked.
14. As Operator, edit previously recorded expense to correct mistakes.
15. As Operator, register new donors with full name and DNI/NIE so contributions tracked for tax purposes.
16. As Operator, update donor contact info (email, phone, address) so records stay current.
17. As Treasurer, perform all Operator actions so can handle data entry when needed.
18. As Treasurer, view donation summary grouped by type for date range — see total tithes, offerings, other income.
19. As Treasurer, view expense summary grouped by category for date range — see where church spending.
20. As Treasurer, view income-vs-expenses balance for date range to assess church financial health.
21. As Treasurer, generate individual donor statement for date range to produce annual certificado de donaciones for tax compliance.
22. As Treasurer, view individual donor contribution history to verify records before issuing tax receipts.
23. As Pastor, view individual donor contributions with amounts for pastoral care context.
24. As Pastor, view donation and expense reports to stay informed about church financial position.
25. As Pastor, have read-only access (no create/modify financial records) so separation of duties maintained.
26. As Operator, deactivate donor (not delete) so historical donation data preserved when someone leaves church.
27. As Operator, list donations with pagination and filtering by date range to find specific records efficiently.
28. As Operator, list expenses with pagination and filtering by date range to review spending records.
29. As Operator, list donors with pagination to browse and search donor directory.
30. As Treasurer, report date ranges default to current calendar year for most common reporting period.
31. As any user, get clear validation error messages indicating which fields invalid and why to fix input.
32. As developer, have Swagger UI in development to explore and test API interactively.

## Implementation Decisions

### Architecture
- Standard layered architecture: Controller → Service → Repository per domain module
- All endpoints versioned under `/api/v1/`
- Page-number pagination via Spring Data `Pageable` on all list endpoints
- Global error handling via `@RestControllerAdvice` with custom error response structure including field-level validation errors

### Modules
1. **Security Module** — Spring Security config with session-based auth, `UserDetailsService` backed by PostgreSQL, role-based method/endpoint authorization, login/logout endpoints
2. **User Management Module** — User CRUD restricted to Admin role, password change endpoint for authenticated users, many-to-many user-role relationship
3. **Donor Module** — Donor CRUD with DNI/NIE validation (Spanish format), soft delete via active/inactive flag, no hard deletes
4. **Donation Module** — Create and edit (no delete), validates positive non-zero amounts, rejects future dates, duplicate detection (same donor + amount + date + type) returning non-blocking warning, supports anonymous donations via nullable donor reference
5. **Expense Module** — Create and edit (no delete), validates positive non-zero amounts, rejects future dates, extensible category enum designed so adding new values needs no breaking changes
6. **Reports Module** — Read-only endpoints for donation summary by type, expense summary by category, income-vs-expenses balance, individual donor statements, all parameterized by date range
7. **Error Handling Module** — `@RestControllerAdvice` producing consistent JSON error body with status, error label, message, optional field-level errors map, timestamp
8. **Audit Infrastructure** — JPA entity listener or `@MappedSuperclass` auto-populating `created_by`, `created_at`, `updated_by`, `updated_at` from Spring Security context

### Database
- PostgreSQL with Flyway migrations
- Monetary amounts stored as `DECIMAL(10,2)` mapped to `BigDecimal` in Kotlin
- Euros only, no multi-currency
- Initial Admin user seeded via Flyway migration with bcrypt-hashed password
- Enums stored as strings in database for readability and safe extensibility

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
- Swagger UI accessible without auth in development (Spring profile)
- Secured or disabled in production (Spring profile)
- OpenAPI spec metadata reflects API version v1

## Testing Decisions

Tests verify external behavior through API surface, not internal implementation. Use real HTTP requests against running application context with real PostgreSQL via Testcontainers — no mocking database layer.

### Donation Module (thorough coverage)
- Valid donation creation returns success and persists correctly
- Reject zero and negative amounts
- Reject future dates
- Anonymous donations (no donor) accepted
- Duplicate detection: same donor + amount + date + type returns warning
- Duplicate warning non-blocking (donation still saved when confirmed)
- Duplicate detection not applied to anonymous donations
- Editing donation works and updates audit fields
- Donations cannot be deleted (no DELETE endpoint, returns 405 or 404)
- Pagination and date range filtering on list endpoint
- Role-based access: Operator and Treasurer can create/edit, Pastor and Admin cannot

### Security Module (thorough coverage)
- Successful login with correct credentials returns session
- Login failure with wrong credentials returns 401
- Unauthenticated requests to protected endpoints return 401
- Unauthorized role access returns 403 (e.g., Operator accessing reports, Pastor creating donation)
- Session logout invalidates session
- Password change with correct current password succeeds
- Password change with wrong current password fails
- Admin can create users with assigned roles
- Non-Admin roles cannot access user management endpoints

### Reports Module (light coverage)
- Each report endpoint returns data in expected structure for given date range
- Report endpoints enforce role-based access (Treasurer and Pastor only)

### Prior Art
Codebase includes Testcontainers config (`TestcontainersConfiguration.kt`) and Spring Boot test class (`DonationsApplicationTests.kt`) loading full application context. New tests follow same pattern — `@Import(TestcontainersConfiguration::class)` with `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `TestRestTemplate` or `MockMvc` for HTTP assertions.

## Out of Scope

- **PDF generation** for tax receipts (certificado de donaciones) — deferred to future phase
- **Top donor ranking report** — may add later
- **Email notifications** or digital receipt delivery
- **Multi-currency support** — euros only
- **Approval workflows** for expenses
- **Hard deletion** of any records (donors, donations, expenses, users)
- **Self-registration** or email-based password reset
- **Frontend / UI** — PRD covers API only
- **Forced password change on first login**

## Further Notes

- Church based in Spain; donor national IDs follow DNI/NIE format
- Expense and donation type enums designed so adding new values = simple code + migration change, no breaking impact on existing data
- Calendar year (Jan–Dec) = fiscal period for report defaults
- Flyway seed migration for initial Admin user uses bcrypt-hashed password, documented for deployment team to change immediately after first setup