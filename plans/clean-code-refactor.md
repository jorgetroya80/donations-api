# Implementation Plan: clean-code refactors

## Overview
Behavior-preserving refactors from clean-code review, vetted by spring-boot-engineer. No API/JSON/event changes. No new ADR needed (ADR-004 shape unchanged). Jorge commits himself; work on a branch, no commits by agent.

## Architecture Decisions
- Extension `CrudRepository<T, Long>.getOrThrow(id, label)` in `infrastructure/error/`; messages stay `"<Label> not found with id: N"`.
- Keep `!!` on create-request fields: DTOs nullable so `@NotNull` yields field-level 400s.
- Keep `findById` (DonationRepository `@EntityGraph`); never `getReferenceById`.
- Skip: merge donation/expense summaries, typed projections (native-image risk), patch-block abstraction in User/Donor/Expense services, `"JSESSIONID"` constant.

## Dependency graph
```
T1 getOrThrow ──► T2 createDonation
T3 writeProblem   (independent)
T4 AuthService + ROLE_PREFIX (independent; touches Role.kt + AppUserDetailsService)
T5 ReportService orZero (independent; small)
```
Each task = one refactor across all its call sites, left green.

## Task List

### Phase 1: Core (high value)
- [x] T1: `getOrThrow` extension + adopt in services (+ expression-body getters)
- [x] T2: Collapse `createDonation` save paths

### Checkpoint: after T1-T2
- [ ] `./gradlew test` green
- [ ] 404 `detail` texts unchanged; duplicate-flow tests pass
- [ ] Review with Jorge

### Phase 2: Duplication + polish
- [x] T3: Shared problem-JSON writer
- [x] T4: `AuthService.login` split + shared `ROLE_PREFIX`
- [ ] T5: `ReportService` `orZero()`

### Checkpoint: Complete
- [ ] `./gradlew test` green
- [ ] `git diff` only touches listed files
- [ ] Jorge commits

## Tasks

### T1: `getOrThrow` extension
**Description:** Add extension; replace `findById(id).orElseThrow { NotFoundException(...) }` in Donation, Donor, Expense, Report, User(`getUser`) services. Fold `getDonation`/`getExpense` into expression bodies.
**Acceptance criteria:**
- [ ] 404 messages identical ("Donation/Donor/Expense/User not found with id: N")
- [ ] `UserService.changeOwnPassword` untouched (different message)
- [ ] Still calls `findById` (EntityGraph preserved)
**Verification:**
- [ ] `./gradlew test`
- [ ] `grep -rn "orElseThrow" src/main` leaves only changeOwnPassword-type cases
**Dependencies:** None
**Files:** `infrastructure/error/RepositoryExtensions.kt` (new), `DonationService.kt`, `DonorService.kt`, `ExpenseService.kt`, `ReportService.kt`, `UserService.kt`
**Scope:** Medium

### T2: Collapse `createDonation`
**Description:** Single save+emit path; `isDuplicate` computed once; import `Donor` (drop FQN).
**Acceptance criteria:**
- [ ] Unconfirmed duplicate: no save, no `DonationCreated`, returns `duplicateDetected()`
- [ ] Confirmed duplicate: saved, one emit, `savedWithWarning`
- [ ] Non-duplicate/no donor: saved, one emit, `saved`
- [ ] Donor NotFound order unchanged (lookup first)
**Verification:**
- [ ] `./gradlew test --tests '*DonationRecordingTest' --tests '*AppEventTest'`
- [ ] Full `./gradlew test`
**Dependencies:** T1
**Files:** `DonationService.kt`
**Scope:** Small

### T3: Shared problem-JSON writer
**Description:** Extract `writeProblem(request, response, objectMapper, status, detail, extras)` (title, instance, `code` before `requestId`, status, UTF-8, problem+json, write, flush). Use in `SecurityConfig` entry point and `PasswordChangeRequiredFilter`.
**Acceptance criteria:**
- [ ] 401 and 403 bodies byte-identical incl. property order (`code` then `requestId`)
- [ ] `GlobalExceptionHandler` untouched unless a shared `newProblem` is clearly cheaper (default: skip)
**Verification:**
- [ ] `./gradlew test --tests '*SecurityIntegrationTest'` + password-change 403 tests
- [ ] Full `./gradlew test`
**Dependencies:** None
**Files:** `infrastructure/error/ProblemResponses.kt` (new), `SecurityConfig.kt`, `PasswordChangeRequiredFilter.kt`
**Scope:** Small-Medium

### T4: `AuthService.login` split
**Description:** Extract `rotateSession` and `roleNames`; shared `ROLE_PREFIX` const (in `Role.kt`) used by `AppUserDetailsService:24` and AuthService.
**Acceptance criteria:**
- [ ] Emit order unchanged: LoginFailed→throw; recordSuccess→LoginSucceeded→session steps
- [ ] Session fixation rotation intact
**Verification:**
- [ ] `./gradlew test --tests '*AuthEventTest' --tests '*SessionSecurityTest' --tests '*SessionCookieTest'`
**Dependencies:** None
**Files:** `AuthService.kt`, `Role.kt`, `AppUserDetailsService.kt`
**Scope:** Small

### T5: `orZero()` in ReportService
**Description:** Private `BigDecimal?.orZero()` replaces 4 `?: BigDecimal.ZERO`. No merge of summaries, no projections.
**Acceptance criteria:**
- [ ] Report JSON unchanged
**Verification:**
- [ ] `./gradlew test --tests '*FinancialReportsTest'`
**Dependencies:** T1 (same file; do after to avoid conflicts)
**Files:** `ReportService.kt`
**Scope:** XS

## Risks and Mitigations
| Risk | Impact | Mitigation |
|------|--------|------------|
| 404 message drift | Med | Label param; tests assert detail |
| JSON property order change (T3) | Med | Set `code` before `requestId`; existing tests |
| Testcontainers flake | Low | Re-run before investigating (known) |
| Native-image | Low | No reflection added; `nativeTest` only if projections touched (skipped) |

## Open Questions
- (resolved) T4/T5 included; branch `chore-clean-code`
