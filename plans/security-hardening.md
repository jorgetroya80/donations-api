# Implementation Plan: Security Hardening — Donations API v1

**Status:** Implemented (2026-06-10). All 8 findings fixed; spec at [docs/specs/security-hardening.md](../docs/security-hardening.md). Deviation: Task 6 used a custom `UserSessionTracker` instead of Spring Security `SessionRegistry` (registry is not populated by manual controller authentication).

## Overview

Security review found 4 HIGH and 4 MEDIUM issues in the auth/session layer. This plan fixes findings 1–8: CSRF exposure (via cookie hardening), session fixation, default `admin/admin` credentials (forced password change on first login), login brute-forcing, session cookie flags, eager session creation, stale sessions after password change, and Postgres LAN exposure. Lows (#9 blank username on update, #10 swagger `permitAll` relies on prod profile) are follow-ups, out of scope.

## Findings Reference

| # | Sev | Finding | Location |
|---|-----|---------|----------|
| 1 | HIGH | CSRF disabled + cookie session auth, no SameSite | `SecurityConfig.kt:34` |
| 2 | HIGH | Session fixation — login reuses pre-auth session ID | `AuthController.kt:33` |
| 3 | HIGH | Seeded `admin`/`admin`, nothing forces rotation | `V3__seed_admin_user.sql` |
| 4 | HIGH | No brute-force protection / auth logging on `/login` | `AuthController.kt` |
| 5 | MED | Session cookie missing `SameSite` / `Secure` flags | `application.yaml` |
| 6 | MED | `SessionCreationPolicy.ALWAYS` | `SecurityConfig.kt:44` |
| 7 | MED | Password change leaves existing sessions valid | `UserService.kt` |
| 8 | MED | Postgres published to all host interfaces | `compose.yaml` |

## Architecture Decisions

- **CSRF mitigation = `SameSite=Lax` cookie, CSRF tokens stay disabled.** Frontend (donations-frontend, ky client, same-origin) sends no CSRF token; SameSite=Lax blocks cross-site state-changing requests in modern browsers with zero frontend change. (User decision.)
- **Default admin = force password change on first login.** New `must_change_password` column + enforcement filter; login response exposes the flag so the frontend can redirect. (User decision.)
- **Brute-force protection = in-memory `LoginAttemptService`, no new dependencies.** 5 consecutive failures → 15-min lock. Resets on restart, not cluster-safe — acceptable for single-instance deployment; documented.
- **Session invalidation = Spring Security `SessionRegistry`**, no Spring Session dependency.
- **V1–V6 migrations are immutable** — schema change ships as new `V7` migration.
- **Secure cookie flag only in `prod` profile** — local dev runs plain HTTP.

## Task List

### Phase 1: Session & Cookie Foundation

#### Task 1: Harden session cookie and session creation policy (fixes #1, #5, #6)

**Description:** Set `SameSite=Lax` and `HttpOnly` on the session cookie, `Secure` in prod profile, and stop creating sessions for anonymous requests.

**Acceptance criteria:**
- [ ] Login `Set-Cookie` includes `SameSite=Lax` and `HttpOnly`
- [ ] `prod` profile config sets `server.servlet.session.cookie.secure: true`
- [ ] Unauthenticated request to `/actuator/health` sets no session cookie (`IF_REQUIRED`)

**Verification:**
- [ ] New assertions in `SecurityIntegrationTest` pass
- [ ] Full suite: `./gradlew test`

**Dependencies:** None

**Files likely touched:**
- `src/main/resources/application.yaml`
- `src/main/kotlin/com/example/donations/infrastructure/config/SecurityConfig.kt`
- `src/test/kotlin/com/example/donations/SecurityIntegrationTest.kt`

**Estimated scope:** S

#### Task 2: Rotate session ID on login (fixes #2)

**Description:** In `AuthController.login`, invalidate any pre-existing session after successful authentication and store the security context in a freshly created session, preventing session fixation (Spring's built-in protection does not cover manual controller authentication).

**Acceptance criteria:**
- [ ] JSESSIONID differs before vs. after login when a pre-auth session existed
- [ ] Pre-login session ID is no longer valid after login

**Verification:**
- [ ] New session-fixation test: obtain session pre-login, log in with that cookie, assert ID changed and old ID rejected
- [ ] Full suite: `./gradlew test`

**Dependencies:** Task 1

**Files likely touched:**
- `src/main/kotlin/com/example/donations/user/AuthController.kt`
- `src/test/kotlin/com/example/donations/SecurityIntegrationTest.kt`

**Estimated scope:** S

### Checkpoint: Foundation
- [ ] All tests pass, build clean
- [ ] `curl -i` login shows `JSESSIONID ... HttpOnly; SameSite=Lax` and a new session ID per login

### Phase 2: Credential Protection

#### Task 3: Login brute-force protection + auth logging (fixes #4)

**Description:** Add in-memory `LoginAttemptService` (ConcurrentHashMap keyed by lowercase username; 5 consecutive failures → 15-min lock; reset on success; injectable clock). `AuthController` checks the lock before authenticating and records outcomes. Failed logins log WARN with username + remote IP; lockouts log INFO. Lockout response is generic (no account enumeration).

**Acceptance criteria:**
- [ ] 5 consecutive bad passwords lock the account; 6th attempt with the correct password is rejected while locked
- [ ] Successful login resets the failure counter
- [ ] Lock expires after 15 minutes (unit-tested via injectable clock)

**Verification:**
- [ ] New `LoginAttemptServiceTest` (unit) passes
- [ ] New lockout integration test passes
- [ ] Full suite: `./gradlew test`

**Dependencies:** Task 2

**Files likely touched:**
- `src/main/kotlin/com/example/donations/user/LoginAttemptService.kt` (new)
- `src/main/kotlin/com/example/donations/user/AuthController.kt`
- `src/main/kotlin/com/example/donations/infrastructure/error/GlobalExceptionHandler.kt`
- `src/test/kotlin/com/example/donations/LoginAttemptServiceTest.kt` (new)
- `src/test/kotlin/com/example/donations/SecurityIntegrationTest.kt`

**Estimated scope:** M

#### Task 4: `must_change_password` schema + entity + DTOs (fixes #3, part 1)

**Description:** New Flyway migration `V7__add_must_change_password.sql` adds `must_change_password BOOLEAN NOT NULL DEFAULT FALSE` to `users` and flags the seeded admin only if its password still equals the V3 default hash. Extend `User` entity, `UserResponse`, and `LoginResponse` with the flag. `createUser` and admin password resets set the flag TRUE (admin-set passwords are provisional); `changeOwnPassword` clears it.

**Acceptance criteria:**
- [ ] Migration applies cleanly on existing schema; still-default admin has flag TRUE
- [ ] Login response and user responses include `mustChangePassword`
- [ ] Admin-created users / admin password resets get flag TRUE; self-service change clears it

**Verification:**
- [ ] `./gradlew test` (Testcontainers runs migrations)
- [ ] Updated `UserManagementTest` assertions pass

**Dependencies:** Task 3

**Files likely touched:**
- `src/main/resources/db/migration/V7__add_must_change_password.sql` (new)
- `src/main/kotlin/com/example/donations/user/User.kt`
- `src/main/kotlin/com/example/donations/user/UserDtos.kt`
- `src/main/kotlin/com/example/donations/user/UserService.kt`
- `src/main/kotlin/com/example/donations/user/AuthController.kt`

**Estimated scope:** M

#### Task 5: Forced-password-change enforcement filter (fixes #3, part 2)

**Description:** `OncePerRequestFilter` registered after the security chain: when the authenticated session is flagged, allow only `PUT /api/v1/users/me/password` and `POST /api/v1/logout`; everything else returns 403 JSON `{"code":"PASSWORD_CHANGE_REQUIRED"}`. Flag cached in a session attribute at login; `changeOwnPassword` clears DB flag and session attribute.

**Acceptance criteria:**
- [ ] Login as flagged user → any business endpoint returns 403 with `PASSWORD_CHANGE_REQUIRED`
- [ ] Password change and logout remain accessible while flagged
- [ ] After password change, previously blocked endpoints work and DB flag is FALSE

**Verification:**
- [ ] New forced-change integration test passes
- [ ] Full suite: `./gradlew test`

**Dependencies:** Task 4

**Files likely touched:**
- `src/main/kotlin/com/example/donations/infrastructure/config/PasswordChangeRequiredFilter.kt` (new)
- `src/main/kotlin/com/example/donations/infrastructure/config/SecurityConfig.kt`
- `src/main/kotlin/com/example/donations/user/UserService.kt`
- `src/test/kotlin/com/example/donations/SecurityIntegrationTest.kt`

**Estimated scope:** M

### Checkpoint: Credential Protection
- [ ] All tests pass
- [ ] Manual: `docker compose up --build` → login `admin/admin` → `/api/v1/donors` returns 403 `PASSWORD_CHANGE_REQUIRED` → change password → access OK
- [ ] Manual: 5 bad passwords → locked; correct password rejected until expiry

### Phase 3: Session Lifecycle & Infrastructure

#### Task 6: Invalidate sessions on password change (fixes #7)

**Description:** Add `SessionRegistry` (`SessionRegistryImpl`) and `HttpSessionEventPublisher` beans; wire `sessionManagement { maximumSessions(-1).sessionRegistry(...) }` for tracking only. On self-service password change, expire all of the user's other sessions (keep current); on admin password reset, expire all of the target user's sessions.

**Acceptance criteria:**
- [ ] Two concurrent logins; password change via session A → session B gets 401 on next request, session A still works
- [ ] Admin password reset expires all target-user sessions

**Verification:**
- [ ] New `SessionSecurityTest` integration test passes
- [ ] Full suite: `./gradlew test`

**Dependencies:** Task 5 (touches same `SecurityConfig` / `UserService` code)

**Files likely touched:**
- `src/main/kotlin/com/example/donations/infrastructure/config/SecurityConfig.kt`
- `src/main/kotlin/com/example/donations/user/UserService.kt`
- `src/test/kotlin/com/example/donations/SessionSecurityTest.kt` (new)

**Estimated scope:** M

#### Task 7: Bind Postgres to localhost only (fixes #8)

**Description:** Change compose port mapping `"5432:5432"` → `"127.0.0.1:5432:5432"`. Keeps local `./gradlew bootRun` against localhost working; removes LAN exposure.

**Acceptance criteria:**
- [ ] `docker compose ps` shows `127.0.0.1:5432->5432`
- [ ] Local bootRun still connects

**Verification:**
- [ ] `docker compose up -d && docker compose ps`
- [ ] `./gradlew -PmainClass=com.example.donations.TestDonationsApplicationKt bootRun` starts (or compose API container healthy)

**Dependencies:** None (parallelizable with any task)

**Files likely touched:**
- `compose.yaml`

**Estimated scope:** XS

#### Task 8: Documentation updates

**Description:** Update `docs/architecture.md`: §7 Security (SameSite cookie, lockout policy, forced-password-change flow, session invalidation), add V7 to migration lists (§5, §9). Record follow-ups (#9, #10) and the in-memory-lockout limitation.

**Acceptance criteria:**
- [ ] Architecture doc reflects all shipped behavior changes
- [ ] Follow-ups #9/#10 recorded

**Verification:**
- [ ] Manual doc review against implemented code

**Dependencies:** Tasks 1–7

**Files likely touched:**
- `docs/architecture.md`
- `plans/security-hardening.md` (check off tasks)

**Estimated scope:** S

### Checkpoint: Complete
- [ ] `./gradlew test` fully green (Docker running for Testcontainers)
- [ ] End-to-end manual flow per Phase 2 checkpoint passes against `docker compose up --build`
- [ ] Ready for review / PR

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Spring Security 7 DSL names differ (`maximumSessions`, `SessionRegistry` wiring) | Med | Verify against Spring Boot 4 / Security 7 docs before Task 6; known gotcha area in this stack |
| `IF_REQUIRED` policy breaks an existing flow that relied on eager sessions | Low | Full integration suite after Task 1; login creates session explicitly |
| Forced-change filter blocks frontend unexpectedly | Med | `mustChangePassword` in login response + distinct `PASSWORD_CHANGE_REQUIRED` code lets frontend redirect; frontend change tracked as follow-up in donations-frontend repo |
| In-memory lockout resets on restart / not cluster-safe | Low | Acceptable single-instance; documented in architecture doc |
| V7 flags a non-default admin by mistake | Low | UPDATE matches username AND the exact V3 hash |

## Parallelization

- Task 7 (compose) is independent — can run any time.
- Tasks 1→2→3→4→5→6 are sequential (shared files: `SecurityConfig`, `AuthController`, `UserService`).
- Task 8 last.

## Open Questions

- Frontend (donations-frontend): who/when implements redirect on `PASSWORD_CHANGE_REQUIRED` and `mustChangePassword`? Out of scope here; needs a ticket in that repo.

## Follow-ups (out of scope)

- #9 LOW: `UpdateUserRequest.username` accepts blank string — add `@Size(min=1)`/`@NotBlank`-style validation on update path.
- #10 LOW: Swagger matchers `permitAll` in all profiles — safe only if prod always runs `prod` profile; consider profile-guarded matchers.
