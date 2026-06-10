# Spec: Security Hardening — Donations API v1

**Status:** Implemented (June 2026) | **Plan:** [plans/security-hardening.md](../plans/security-hardening.md)

## Objective

Harden the authentication/session layer so a deployed instance is not exploitable via CSRF, session fixation, default credentials, or credential brute-forcing. Success criterion: each finding has a test that failed before the fix and passes after it.

## Findings and Resolutions

| # | Sev | Finding | Resolution |
|---|-----|---------|------------|
| 1 | HIGH | CSRF disabled + cookie session auth, no SameSite | Session cookie `SameSite=Lax` + `HttpOnly` (`Secure` in prod). CSRF tokens deliberately not enabled: frontend is same-origin and SameSite=Lax blocks cross-site state-changing requests |
| 2 | HIGH | Session fixation — login reused pre-auth session | `AuthController` invalidates any pre-auth session and creates a fresh one after authentication |
| 3 | HIGH | Seeded `admin`/`admin` credentials | `must_change_password` flag (V7): flagged users are blocked from all endpoints except password change and logout until they rotate; V7 only flags the admin if its password is still the seeded default |
| 4 | HIGH | No brute-force protection on `/login` | `LoginAttemptService`: 5 consecutive failures lock the account 15 min; failures and lockouts logged with remote IP; lockout response is a generic 401 (no account enumeration) |
| 5 | MED | Session cookie missing flags | Covered by #1 |
| 6 | MED | Sessions created for anonymous requests | `SessionCreationPolicy.IF_REQUIRED` |
| 7 | MED | Password change left existing sessions valid | `UserSessionTracker`: self-service change revokes the user's other sessions; admin reset revokes all of the target's sessions |
| 8 | MED | Postgres published on all host interfaces | compose binds `127.0.0.1:5432:5432` |

## Decisions

- **CSRF = SameSite=Lax only** (user decision): the frontend (donations-frontend, ky client, `credentials: include`) is same-origin and sends no CSRF token. Enabling Spring CSRF would require a coordinated frontend change for marginal gain.
- **Default admin = forced password change** (user decision): login succeeds but every other endpoint returns 403 `{"code":"PASSWORD_CHANGE_REQUIRED"}` until the password is rotated. `LoginResponse.mustChangePassword` lets the frontend redirect. Admin-provisioned passwords (create user, admin reset) are always provisional.
- **No Spring Security SessionRegistry**: it is only populated by filter-chain authentication, which the manual login in `AuthController` bypasses, and `expireNow()` requires `ConcurrentSessionFilter` to take effect. `UserSessionTracker` invalidates `HttpSession` objects directly.
- **In-memory lockout and session tracking**: state resets on restart and does not cluster. Acceptable for a single-instance deployment; revisit if the API is ever scaled horizontally.

## Commands

- Build: `./gradlew build`
- Test: `./gradlew test` (Testcontainers; Docker must be running)
- Run local: `./gradlew -PmainClass=com.example.donations.TestDonationsApplicationKt bootRun`

## Boundaries

- **Always:** TDD per fix (failing test first), full test suite before commit, surgical changes.
- **Ask first:** new Gradle dependencies, schema changes beyond V7.
- **Never:** weaken `@PreAuthorize` rules, commit secrets, edit applied Flyway migrations (V1–V7 immutable).

## Success Criteria (all verified by tests)

- Login `Set-Cookie` contains `HttpOnly` and `SameSite=Lax`; anonymous requests get no session cookie (`SessionCookieTest`)
- Session ID changes on login; pre-auth session invalidated (`SecurityIntegrationTest`)
- 5 failed logins lock the account; correct password rejected while locked; lock expires after 15 min (`LoginAttemptServiceTest`, `SecurityIntegrationTest`)
- Flagged user gets 403 `PASSWORD_CHANGE_REQUIRED` everywhere except password change/logout; access restored after rotation (`SecurityIntegrationTest`)
- Password change revokes other sessions (self) or all sessions (admin reset) (`SessionSecurityTest`)

## Open Questions / Follow-ups

- **#9 LOW — fixed:** `UpdateUserRequest.username` rejects blank strings (`@Pattern`; null still allowed for partial updates). Test: `UserManagementTest`.
- **#10 LOW — fixed:** Swagger route matchers are `permitAll` only while springdoc is enabled (`springdoc.api-docs.enabled`); with springdoc disabled (prod) those routes require authentication. Test: `SwaggerSecurityTest`.
- **Frontend (donations-frontend):** handle 403 `PASSWORD_CHANGE_REQUIRED` and `mustChangePassword` in the login response by redirecting to a change-password screen. Needs a ticket in that repo.
