# Implementation Plan: Structured Domain & Security Event Logging

## Overview

Implements [PRD-8](../docs/PRD-8.md) (issue #47) under [ADR-005](../docs/decisions/ADR-005-event-logging-personal-data-policy.md).

Replaces the codebase's four ad-hoc log statements with a closed, typed event vocabulary named per the OWASP Application Logging Vocabulary, correlated per request via MDC, and rendered readable in `dev` / structured JSON in `prod`. Donor personal data is excluded by construction — each event type declares its fields, so there is no field to hold a national ID.

**Success criterion (from PRD-8):** given a report that something failed, find the correlation id, pull every event from that request, and understand what happened — without querying the database and without adding temporary logging.

## Architecture Decisions

- **`AppEvent` is a sealed hierarchy**, one type per event, each declaring `name`, `level`, and its own fields. This is what makes the PII rule a compile-time property rather than review discipline (ADR-005).
- **`EventLogger` is the only holder of an SLF4J logger** in production code. Single method, expected never to change.
- **Actor username is read from `SecurityContextHolder` at emit time, not written to MDC by a filter.** The MDC filter runs at highest precedence — before authentication — so the username isn't available to it. Reading at emit time is always correct and removes a moving part. *(Refinement on PRD-8, which implied the filter attaches both.)*
- **Correlation id only in MDC**, set at highest filter precedence so even filter-chain 401s carry one.
- **Format via Spring Boot's native structured logging**, profile-switched in `application.yaml` alongside the existing CORS/cookie splits. No new dependency.
- **Stacktraces are retained** on unhandled exceptions; residual PII risk accepted and recorded in ADR-005.
- **Reads emit nothing** — lists, gets, reports are silent.
- **Never commit** — verify, report, Jorge commits (standing workflow rule).

### Resolved — login failure events (supersedes PRD-8 stories 15/16)

PRD-8 listed both `authn_login_fail_max` and `authn_login_lock`. In this codebase they fire at the *same instant* — `LoginAttemptService.recordFailure` locks the account exactly when the count hits `MAX_FAILURES` — so emitting both is duplicate noise. OWASP separates them because a lock can have causes other than max retries, which is what its `reason` field is for; here `maxretries` is the only cause.

The login path has **three** distinct observable moments, and the naive mapping collapses the wrong pair:

| Moment | Code | Event |
|---|---|---|
| Credentials rejected | `user/AuthService.kt:37` | `authn_login_fail:userid,locked=false` |
| Threshold crossed, lock applied | `user/LoginAttemptService.kt:39` | `authn_login_lock:userid,reason=maxretries,maxlimit` |
| Attempt against an already-locked account | `user/AuthService.kt:26-28` | `authn_login_fail:userid,locked=true` |

**Decision:** `authn_login_fail_max` is not implemented. `authn_login_fail` gains a `locked` field so attempts against a locked account — the strongest brute-force signal available, and otherwise indistinguishable from a typo'd password — are queryable. Two events, three answerable questions, no refactor of `LoginAttemptService`.

**ADR note:** this mapping is recorded directly in [ADR-005](../docs/decisions/ADR-005-event-logging-personal-data-policy.md) — see its *"Login failures map to three moments, not two"* decision bullet and the rejected *"Emit both `authn_login_fail_max` and `authn_login_lock`"* alternative. The ADR is the authority; this section is the implementation-facing restatement. Task 11's close-out check still verifies the shipped catalogue against it.

## Dependency Graph

```
Task 1 (AppEvent + EventLogger + first event)
    ├── Task 4 (authn: fail, lock)
    ├── Task 5 (authn_password_change)
    ├── Task 6 (authz_fail, authz_admin)
    ├── Task 7 (donation events)
    ├── Task 8 (donor events)
    ├── Task 9 (expense events)
    └── Task 10 (error_unexpected)

Task 2 (MDC correlation filter)   [independent of Task 1]
Task 3 (profile-switched format)  [independent of Tasks 1-2]
```

Tasks 1–3 are the tracer bullet and must all land before Checkpoint 1 — an event without correlation or format doesn't demonstrate the success criterion. Tasks 4–6 touch different files and are parallel-safe. Tasks 7–9 are parallel-safe with each other and with 4–6. Task 10 is last because it depends on the correlation id being real.

---

## Phase 1: Tracer Bullet — one event, end to end

## Task 1: `AppEvent` hierarchy + `EventLogger`, with one event

**Description:** Create the sealed `AppEvent` hierarchy carrying `name`, `level`, and a declared field map, plus the `EventLogger` component that writes an event through SLF4J's fluent `addKeyValue` API at the event's level and reads the actor username from `SecurityContextHolder`. Implement exactly one event — `authn_login_success:userid` — and emit it from `AuthService.login`. Pure Kotlin for the hierarchy; no Spring context needed to test it.

**Acceptance criteria:**
- [ ] Sealed `AppEvent` type exists; each event declares its own name, level, and fields
- [ ] `EventLogger` exposes a single emit method and is the only production class obtaining a `Logger`
- [ ] `authn_login_success` emitted on successful login with `userid`
- [ ] No event type declares a field for national id, name, address, email, password, or session id

**Verification:**
- [ ] Unit test: event produces expected name, level, and exact field set — no Spring context, no Testcontainers (model: `LoginAttemptServiceTest`)
- [ ] Unit test: PII guard — reflectively assert no event type in the hierarchy declares a forbidden field name
- [ ] `./gradlew test` green

**Dependencies:** None
**Files likely touched:** new event + emitter under `infrastructure/`, `user/AuthService.kt`, new test
**Estimated scope:** M

## Task 2: Request correlation filter (MDC)

**Description:** `OncePerRequestFilter` at highest precedence that generates a request id, puts it in MDC, and clears MDC in a `finally` so context cannot leak between pooled threads. Prior art for a `@Component` filter: `infrastructure/config/PasswordChangeRequiredFilter.kt`.

**Acceptance criteria:**
- [ ] Every request has a correlation id in MDC during handling
- [ ] MDC is cleared after the request completes, including when the handler throws
- [ ] Filter ordered ahead of the security chain so anonymous 401s also carry an id

**Verification:**
- [ ] Unit test: id present during chain execution, absent after
- [ ] Unit test: id cleared even when the downstream chain throws
- [ ] `./gradlew test` green

**Dependencies:** None
**Files likely touched:** new filter under `infrastructure/`, new test
**Estimated scope:** S

## Task 3: Profile-switched log format

**Description:** Configure Spring Boot's built-in structured logging in `src/main/resources/application.yaml`: unset under `dev` (readable console pattern), JSON format under `prod`. No new dependency, no `logback-spring.xml`.

**Acceptance criteria:**
- [ ] `prod` profile produces JSON console output including MDC fields
- [ ] `dev` profile keeps the default human-readable pattern
- [ ] No new entry in `build.gradle`

**Verification:**
- [ ] Profile-sensitive test asserting the structured-format property resolves per profile (model: `DeployConfigTest`)
- [ ] Manual: run under each profile, eyeball one login event in both renderings
- [ ] `./gradlew test` green

**Dependencies:** None
**Files likely touched:** `src/main/resources/application.yaml`, new test
**Estimated scope:** S

- [ ] **CHECKPOINT 1:** Full suite green. Log in under `dev` and under `prod`; one `authn_login_success` event appears in both renderings, carrying a correlation id. This is the vertical proven — report to Jorge before widening.

---

## Phase 2: Security Events

## Task 4: Remaining authentication events

**Description:** Add `authn_login_fail:userid,locked` (WARN) and `authn_login_lock:userid,reason,maxlimit` (WARN), per the three-moment mapping resolved above. Emit from `AuthService.login` (`:27`, `:37`) and `LoginAttemptService.recordFailure` (`:40`), **deleting the three existing raw `log.warn`/`log.info` statements**. Client IP rides on authentication events per OWASP.

**Acceptance criteria:**
- [ ] Failed authentication emits `authn_login_fail` with userid, client IP, and `locked=false`
- [ ] Attempt against an already-locked account emits `authn_login_fail` with `locked=true` — and does **not** emit a second lock event
- [ ] `authn_login_lock` emitted once, at the threshold, with `reason=maxretries` and the configured limit
- [ ] `authn_login_fail_max` is not emitted anywhere
- [ ] No raw `LoggerFactory` usage remains in `AuthService` or `LoginAttemptService`

**Verification:**
- [ ] Unit tests for the new event types (name, level, fields)
- [ ] Unit test: N failures produce N `authn_login_fail` events and exactly one `authn_login_lock`
- [ ] Unit test: further attempts after lock produce `locked=true` failures and no additional lock event
- [ ] `LoginAttemptServiceTest` still green after the logger is replaced by the emitter
- [ ] `./gradlew test` green

**Dependencies:** Task 1
**Files likely touched:** `user/AuthService.kt`, `user/LoginAttemptService.kt`, event types, tests
**Estimated scope:** M

## Task 5: Password-change events

**Description:** Add `authn_password_change:userid` (INFO) and `authn_password_change_fail:userid` (ERROR — SLF4J has no CRITICAL, per ADR-005). Emit from `UserService.changeOwnPassword` (`:68`).

**Acceptance criteria:**
- [ ] Successful self-service password change emits `authn_password_change`
- [ ] Failed attempt (wrong current password) emits `authn_password_change_fail` at ERROR
- [ ] No password value or session id appears in either event

**Verification:**
- [ ] Unit tests for both event types
- [ ] `./gradlew test` green

**Dependencies:** Task 1
**Files likely touched:** `user/UserService.kt`, event types, tests
**Estimated scope:** S

## Task 6: Authorization events

**Description:** Add `authz_fail:userid,resource` (ERROR) emitted from the `AccessDeniedException` handler in `infrastructure/error/GlobalExceptionHandler.kt:40`, and `authz_admin:userid,event` (WARN) from admin user-management operations in `UserService.createUser` (`:24`) and `updateUser` (`:41`). Where a role changes, prefer `authz_change:userid,from,to`.

**Acceptance criteria:**
- [ ] 403 from a role denial emits `authz_fail` with the acting user and the requested resource path
- [ ] Admin user creation emits `authz_admin`
- [ ] Role change on update emits `authz_change` with previous and new level
- [ ] The `PasswordChangeRequiredFilter` 403 does **not** emit `authz_fail` — it is a workflow gate, not a privilege denial

**Verification:**
- [ ] Unit tests for the three event types
- [ ] Existing `SecurityIntegrationTest` / `UserManagementTest` still green
- [ ] `./gradlew test` green

**Dependencies:** Task 1
**Files likely touched:** `infrastructure/error/GlobalExceptionHandler.kt`, `user/UserService.kt`, event types, tests
**Estimated scope:** M

- [ ] **CHECKPOINT 2:** Full suite green. Security vocabulary complete and matching ADR-005; no raw logger calls left in the auth path. Report to Jorge.

---

## Phase 3: Domain Events

## Task 7: Donation events

**Description:** `donation_create:donationId,donorId,amount` and `donation_update:donationId` from `donation/DonationService.kt` (`:30`, `:61`). `listDonations` and `getDonation` emit nothing.

**Acceptance criteria:**
- [ ] Create emits with donation id, donor id, amount — no donor name or national id
- [ ] Update emits with donation id
- [ ] Read paths emit nothing

**Verification:**
- [ ] Unit tests for both event types
- [ ] `./gradlew test` green

**Dependencies:** Task 1
**Files likely touched:** `donation/DonationService.kt`, event types, tests
**Estimated scope:** S

## Task 8: Donor events

**Description:** `donor_create:donorId` and `donor_update:donorId` from `donor/DonorService.kt` (`:31`, `:48`). This is the highest-risk call site for the PII rule — the donor aggregate is exactly what must not be logged.

**Acceptance criteria:**
- [ ] Create and update emit the donor id only
- [ ] No donor field — name, national id, address, email — is reachable from either event type
- [ ] Search and read paths emit nothing

**Verification:**
- [ ] Unit tests for both event types
- [ ] PII guard test from Task 1 covers these types
- [ ] `./gradlew test` green

**Dependencies:** Task 1
**Files likely touched:** `donor/DonorService.kt`, event types, tests
**Estimated scope:** S

## Task 9: Expense events

**Description:** `expense_create:expenseId,amount,category` and `expense_update:expenseId` from `expense/ExpenseService.kt` (`:28`, `:41`).

**Acceptance criteria:**
- [ ] Create emits expense id, amount, category
- [ ] Update emits expense id
- [ ] Read paths emit nothing

**Verification:**
- [ ] Unit tests for both event types
- [ ] `./gradlew test` green

**Dependencies:** Task 1
**Files likely touched:** `expense/ExpenseService.kt`, event types, tests
**Estimated scope:** S

- [ ] **CHECKPOINT 3:** Full suite green. Complete event catalogue matches PRD-8 and ADR-005. Report to Jorge.

---

## Phase 4: Error Path & Close-out

## Task 10: Unexpected-error event

**Description:** Emit `error_unexpected:requestId,exceptionType` from `GlobalExceptionHandler.handleAll` (`:59-63`), **keeping the existing stacktrace log** per ADR-005. The correlation id is what ties a user's report to the event stream.

**Acceptance criteria:**
- [ ] Unhandled exception emits the event with correlation id and exception class name
- [ ] Stacktrace still logged at ERROR
- [ ] The exception *message* is not copied into an event field (it can carry field values)

**Verification:**
- [ ] Unit test for the event type
- [ ] `./gradlew test` green

**Dependencies:** Tasks 1, 2
**Files likely touched:** `infrastructure/error/GlobalExceptionHandler.kt`, event type, test
**Estimated scope:** S

## Task 11: Documentation close-out

**Description:** Record the event catalogue in `docs/architecture.md` so the vocabulary is discoverable outside the code. Confirm the shipped catalogue matches ADR-005; if it diverged, the ADR is immutable — write a superseding ADR rather than editing it.

**Acceptance criteria:**
- [ ] Event catalogue documented with names, levels, and fields
- [ ] Any divergence from ADR-005 is either corrected in code or captured in a superseding ADR
- [ ] PRD-8 issue #47 closed

**Verification:**
- [ ] Manual review against ADR-005

**Dependencies:** Tasks 1–10
**Files likely touched:** `docs/architecture.md`
**Estimated scope:** S

- [ ] **CHECKPOINT 4 (success criterion):** Trigger a deliberate failure under the `prod` profile. Take only the correlation id from the error response, recover every event from that request, and explain what happened — **without** querying the database and **without** adding logging. If this is not possible, the event set is wrong regardless of implementation quality. Full suite green. **NO COMMIT** — report and wait.
