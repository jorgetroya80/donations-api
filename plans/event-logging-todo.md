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
- [ ] Task 7: `donation_create` / `donation_update`
- [ ] Task 8: `donor_create` / `donor_update` (highest PII risk)
- [ ] Task 9: `expense_create` / `expense_update`
- [ ] CHECKPOINT 3: full tests green, catalogue matches ADR-005, report to Jorge

## Phase 4: Error Path & Close-out
- [ ] Task 10: `error_unexpected` (keep stacktrace)
- [ ] Task 11: Document catalogue in `docs/architecture.md`, close #47
- [ ] CHECKPOINT 4: reconstruct a failure from correlation id alone — no DB, no added logging. NO COMMIT — report and wait
