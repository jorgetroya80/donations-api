# Implementation Plan: API Standards & Architecture Remediation


## Overview

A codebase review (2026-07-16) found the API architecturally healthy but with standards gaps. Confirmed scope with Jorge: **full RFC 9457 ProblemDetail error migration** plus **core fixes** — login validation, `AuthService` extraction, 201/`@PreAuthorize` consistency, lazy-donor verification, date-range dedupe. Deferred: OpenAPI annotations, PATCH semantics, polish items (typed projections, `@Transactional` style, session-tracker placement).

## Architecture Decisions

- **RFC 9457 `ProblemDetail`** replaces custom `ErrorResponse` for all error bodies, including the filter-level 401 from `SecurityConfig` (today a divergent hand-rolled JSON string). Contract change → **ADR-004** + api-client major bump on next publish.
- Validation field errors ride as a `fields` extension property on `ProblemDetail` (preserves current UX for form errors).
- Login orchestration moves to a new `user/AuthService.kt`; `AuthController` keeps only HTTP mapping (removes the codebase's sole controller→repository dependency).
- Majority conventions win: `@ResponseStatus(CREATED)` + raw DTO for creates; class-level `@PreAuthorize` with method-level overrides.
- **Never commit** — verify, report, Jorge commits (standing workflow rule).

## Dependency Graph

```
Task 1 (ADR-004)
    └── Task 2 (GlobalExceptionHandler → ProblemDetail + test updates)
            └── Task 3 (SecurityConfig 401 → same ProblemDetail shape)
Task 4 (login validation + AuthDtos)
    └── Task 5 (extract AuthService)        [same file: sequential]
Task 6 (UserController consistency)         [independent]
Task 7 (lazy donor verification/fix)        [independent]
Task 8 (date-range helper)                  [independent]
```

Tasks 6–8 are parallel-safe with each other; 2→3 and 4→5 are sequential. Error migration goes first (highest risk, most test churn — fail fast).

## Task List

### Phase 1: Error Contract (ProblemDetail)

## Task 1: Write ADR-004 — RFC 9457 ProblemDetail error responses

**Description:** Document the decision to replace the custom `ErrorResponse` shape with RFC 9457 `application/problem+json`, including the `fields` extension, the unification of the filter-level 401, and the api-client major-bump consequence. Copy `docs/decisions/ADR-template.md`, fill all six sections, add index row in `docs/decisions/README.md`.

**Acceptance criteria:**
- [ ] `docs/decisions/ADR-004-problem-detail-errors.md` exists with all six sections filled, status Accepted
- [ ] Index row added to `docs/decisions/README.md`
- [ ] Consequences section names the error-contract break and api-client major bump

**Verification:**
- [ ] Manual: ADR follows the template structure; README index renders correctly

**Dependencies:** None
**Files likely touched:** `docs/decisions/ADR-004-problem-detail-errors.md` (new), `docs/decisions/README.md`
**Estimated scope:** S

## Task 2: Migrate GlobalExceptionHandler to ProblemDetail

**Description:** Convert every handler in `infrastructure/error/GlobalExceptionHandler.kt` to return `ProblemDetail` (Spring native support): `title` from reason phrase, `detail` from message, validation errors as a `fields` extension map. Delete `ErrorResponse.kt` (or keep only if still referenced — expect not). Update all integration tests asserting on the old `{status,error,message,fields,timestamp}` shape.

**Acceptance criteria:**
- [ ] All 9 exception handlers return `ProblemDetail` with `application/problem+json` content type
- [ ] Validation errors (400) expose per-field messages under a `fields` extension property
- [ ] `ErrorResponse.kt` removed; no dangling references

**Verification:**
- [ ] `./gradlew test` green (feature tests asserting error bodies updated: `DonationRecordingTest`, `DonorManagementTest`, `ExpenseRecordingTest`, `UserManagementTest`, `SecurityIntegrationTest`)
- [ ] Manual: `POST /api/v1/donors` with invalid body → 400, `Content-Type: application/problem+json`, `fields` present

**Dependencies:** Task 1
**Files likely touched:** `infrastructure/error/GlobalExceptionHandler.kt`, `infrastructure/error/ErrorResponse.kt` (delete), ~5 test files
**Estimated scope:** M

## Task 3: Unify the filter-level 401 with ProblemDetail

**Description:** Replace the hand-written JSON string in `SecurityConfig`'s `authenticationEntryPoint` (SecurityConfig.kt:47-54) with a `ProblemDetail` serialized via the injected `ObjectMapper`, so unauthenticated 401s match handler-produced errors exactly.

**Acceptance criteria:**
- [ ] Filter-level 401 body is byte-for-byte the same shape as a handler-level 401 (same fields, same content type)
- [ ] No hand-rolled JSON strings remain in `SecurityConfig`

**Verification:**
- [ ] `./gradlew test --tests '*SecurityIntegrationTest*'` green
- [ ] Manual: unauthenticated `GET /api/v1/donations` → 401 `application/problem+json`

**Dependencies:** Task 2
**Files likely touched:** `infrastructure/config/SecurityConfig.kt`, `SecurityIntegrationTest.kt`
**Estimated scope:** S

### Checkpoint: Error Contract
- [ ] Full `./gradlew test` green
- [ ] All error responses (400/401/403/404/409/500) share one ProblemDetail shape — spot-check via curl against local run
- [ ] springdoc spec still generates (`OpenApiTest`)
- [ ] Report to Jorge before Phase 2

### Phase 2: Auth Hardening

## Task 4: Validate login request + move auth DTOs

**Description:** Move `LoginRequest`/`LoginResponse` out of `AuthController.kt` into a new `user/AuthDtos.kt` (matching every other feature's `*Dtos.kt` convention). Add `@field:NotBlank` constraints to `LoginRequest` and `@Valid` on the login `@RequestBody`.

**Acceptance criteria:**
- [ ] `POST /api/v1/login` with blank/missing username or password → 400 ProblemDetail with `fields`, never reaching the `AuthenticationManager`
- [ ] DTOs live in `user/AuthDtos.kt`; `AuthController.kt` declares no data classes

**Verification:**
- [ ] New/updated integration test: blank-credentials login → 400 (not 401)
- [ ] `./gradlew test --tests '*Security*' --tests '*User*'` green

**Dependencies:** Task 2 (error shape asserted in new test)
**Files likely touched:** `user/AuthDtos.kt` (new), `user/AuthController.kt`, one test file
**Estimated scope:** S

## Task 5: Extract AuthService from AuthController

**Description:** Create `user/AuthService.kt` owning the login flow currently in `AuthController.login` (lines 34–74): lockout check, `authenticationManager.authenticate`, failure/success recording, session-fixation rotation, security-context wiring, `mustChangePassword` lookup, session registration. Controller shrinks to: call service, map to `LoginResponse`. Preserve the session-rotation comment/behavior exactly (ADR-001).

**Acceptance criteria:**
- [ ] `AuthController` no longer injects `UserRepository`, `LoginAttemptService`, or `UserSessionTracker` — only `AuthService`
- [ ] Behavior unchanged: lockout → 401 Locked path, session ID rotates on login, `mustChangePassword` flag set on session
- [ ] No repository is injected by any controller in the codebase (grep check)

**Verification:**
- [ ] `./gradlew test --tests '*Session*' --tests '*Security*' --tests '*Login*'` green
- [ ] Full `./gradlew test` green

**Dependencies:** Task 4 (same file)
**Files likely touched:** `user/AuthService.kt` (new), `user/AuthController.kt`
**Estimated scope:** M

### Checkpoint: Auth
- [ ] Full test suite green
- [ ] Manual login flow works end-to-end (login → cookie → authorized call → logout) via curl/httpie against local `TestDonationsApplication`
- [ ] Report to Jorge before Phase 3

### Phase 3: Consistency & Robustness

## Task 6: Standardize UserController (201 style + @PreAuthorize placement)

**Description:** Align `UserController` with the majority convention: class-level `@PreAuthorize("hasRole('ADMIN')")`, method-level `@PreAuthorize("isAuthenticated()")` override kept on `changeOwnPassword`; `createUser` returns raw DTO with `@ResponseStatus(HttpStatus.CREATED)` instead of `ResponseEntity.status(...)`. Leave `DonationController`'s deliberate 200-or-201 duplicate-detection flow untouched.

**Acceptance criteria:**
- [ ] `POST /api/v1/users` still returns 201 with the same body
- [ ] Non-ADMIN still 403 on all admin endpoints; any authenticated user can still change own password

**Verification:**
- [ ] `./gradlew test --tests '*UserManagement*' --tests '*Security*'` green

**Dependencies:** None
**Files likely touched:** `user/UserController.kt`
**Estimated scope:** XS

## Task 7: Verify (and fix if needed) lazy donor access in DonationResponse

**Description:** `DonationResponse.from` reads `donation.donor?.fullName` (DonationDtos.kt:61-62) in the controller, outside the service transaction, with `open-in-view: false` and `Donation.donor` LAZY. Determine whether list/get paths initialize the association safely; if fragile, add a fetch join or `@EntityGraph` to `DonationRepository` query methods.

**Acceptance criteria:**
- [ ] A test exists that lists and fetches donations having donors and asserts `donorName` is populated (proves no `LazyInitializationException`)
- [ ] If a fix was needed: no N+1 on the list path (single query with join, verified via SQL logging in test)

**Verification:**
- [ ] `./gradlew test --tests '*DonationRecording*'` green

**Dependencies:** None
**Files likely touched:** `donation/DonationRepository.kt`, `DonationRecordingTest.kt`
**Estimated scope:** S

## Task 8: Deduplicate default date-range logic

**Description:** The `from ?: LocalDate.of(Year.now().value, 1, 1)` / `to ?: LocalDate.of(..., 12, 31)` pair repeats 6+ times (`DonationService.kt:20-21`, `ExpenseService.kt:18-19`, `ReportService.kt:24-25,45-46,66-67,85-86`). Extract one small shared helper (e.g. top-level function in a shared file) and replace all occurrences. No behavior change.

**Acceptance criteria:**
- [ ] Single definition of the default-year-range logic; all 6+ call sites use it
- [ ] No behavior change (existing tests unmodified and green)

**Verification:**
- [ ] `./gradlew test` green with no test edits in this task

**Dependencies:** None
**Files likely touched:** new shared helper file, `donation/DonationService.kt`, `expense/ExpenseService.kt`, `report/ReportService.kt`
**Estimated scope:** S

### Checkpoint: Complete
- [ ] Full `./gradlew test` green (Testcontainers suite)
- [ ] Manual smoke: login, create donor, invalid-body 400, unauthenticated 401 — all ProblemDetail
- [ ] `OpenApiTest` green; spec regenerates
- [ ] **Do not commit** — report diff summary to Jorge and wait

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Error-contract break ripples into frontend/api-client | High | ADR-004 first; api-client major bump on publish; Jorge coordinates frontend update |
| Test churn in Phase 1 larger than expected | Med | Error-shape assertions are localized; update via one shared assertion helper if repeated |
| AuthService extraction subtly changes session behavior | Med | Behavior-preserving move only; session/security integration tests are the gate |
| Task 7 uncovers a real production bug (lazy init) | Low | Fix is a fetch join — contained; tests prove it |

## Open Questions

- None — scope and error-format decision confirmed with Jorge on 2026-07-16.

---

## plans/todo.md (to materialize on approval)

```markdown
# TODO — API Standards & Architecture Remediation

## Phase 1: Error Contract
- [ ] Task 1: ADR-004 — ProblemDetail error responses
- [ ] Task 2: Migrate GlobalExceptionHandler to ProblemDetail (+ test updates)
- [ ] Task 3: Unify filter-level 401 in SecurityConfig
- [ ] CHECKPOINT: full tests green, one error shape everywhere, report to Jorge

## Phase 2: Auth Hardening
- [ ] Task 4: Validate LoginRequest + move DTOs to AuthDtos.kt
- [ ] Task 5: Extract AuthService from AuthController
- [ ] CHECKPOINT: full tests green, manual login flow, report to Jorge

## Phase 3: Consistency & Robustness
- [ ] Task 6: Standardize UserController (201 + @PreAuthorize)
- [ ] Task 7: Verify/fix lazy donor access (test + fetch join if needed)
- [ ] Task 8: Deduplicate default date-range helper
- [ ] CHECKPOINT: full suite green, smoke test, NO COMMIT — report and wait
```

## Deferred (follow-up work, not in this pass)

- OpenAPI operation/response/security-scheme annotations (improves generated TS client)
- PATCH vs PUT decision for partial updates (contract change; own ADR)
- Typed report projections, `@Transactional` style unification, session-tracker move to service layer
