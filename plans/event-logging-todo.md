# TODO — Structured Domain & Security Event Logging

Plan: [event-logging.md](event-logging.md) · PRD: [PRD-8](../docs/PRD-8.md) (#47) · ADR: [ADR-005](../docs/decisions/ADR-005-event-logging-personal-data-policy.md)

Nothing blocked. Login-failure event mapping resolved in the plan (three moments, two events, `authn_login_fail_max` dropped as redundant).

## Phase 1: Tracer Bullet
- [x] Task 1: `AppEvent` sealed hierarchy + `EventLogger` + `authn_login_success` end to end
- [x] Task 2: Request correlation filter (MDC set + cleared)
- [x] Task 3: Profile-switched structured log format (+ `logging.pattern.correlation` for dev)
- [x] CHECKPOINT 1: one correlated event visible in both `dev` and `prod` rendering, report to Jorge
  - **Open for Jorge:** adopt `%kvp` so dev shows event fields, not just the event name? See plan → Checkpoint 1 note.

## Phase 2: Security Events
- [x] Task 4: `authn_login_fail` (with `locked` field), `authn_login_lock` — delete the 3 raw log statements
- [x] Task 5: `authn_password_change` / `_fail`
- [x] Task 6: `authz_fail`, `authz_admin`, `authz_change`
- [x] CHECKPOINT 2: full tests green, no raw loggers left in auth path, report to Jorge
  - **Open for Jorge:** `authz_admin` does not identify *who* was created/updated — only the acting admin. Adding a `targetId` field would close it. See plan → Phase 2 note.

## Phase 3: Domain Events
- [x] Task 7: `donation_create` / `donation_update` (+ `targetId` amendment to `authz_admin`)
- [x] Task 8: `donor_create` / `donor_update` (highest PII risk)
- [x] Task 9: `expense_create` / `expense_update`
- [x] CHECKPOINT 3: full tests green, catalogue matches ADR-005, report to Jorge
  - ADR-005 amended mid-phase: `actor` permitted on all events (implementation contradicted the original wording).

## Phase 4: Error Path & Close-out
- [x] Task 10: `error_unexpected` (keep stacktrace) — plus `requestId` on every error response
- [x] Task 11: Document catalogue in `docs/architecture.md`
- [x] CHECKPOINT 4: **met** — a 500 reconstructed from the correlation id alone, no DB, no added logging
- [ ] **#47 deliberately left open** — the work is unmerged on `feat-monitoring`. Close it on merge, via `Closes #47` in the PR.

## Follow-ups for Jorge
- [ ] api-client: additive change — `requestId` now on every error response. Minor bump + changelog line ("include `requestId` in bug reports"). Strict-deserialization clients would reject the unknown property.
- [ ] `CLAUDE.md`'s `bootRun` command is wrong: `-PmainClass=…TestDonationsApplicationKt` has no wiring in `build.gradle`, so it starts `DonationsApplication` against `localhost:5432` instead of Testcontainers. Unrelated to this work.
