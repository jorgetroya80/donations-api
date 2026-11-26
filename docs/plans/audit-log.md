# Implementation Plan: Audit Log

## Overview

Implements [`docs/specs/audit-log.md`](../specs/audit-log.md) (approved 2026-11-23) — decision in [ADR-006](../decisions/ADR-006-audit-log.md): an append-only `audit_log` table recording who and when for every create/update of donations, expenses, donors and users. App-written, same transaction as the change, identifiers only. Work on branch `feat/audit-log`; Jorge commits.

## Progress

- [x] T1 ADR-006 + README index row + link from spec
- [x] T2 Tracer: V9 + AuditLogEntry/Repository/Service + DonationService + AuditLogTest (needs T1 reviewed)
- [x] **Checkpoint 1:** `./gradlew test` green, V9 applies on local DB, review with Jorge
- [x] T3 Expense + donor write points (needs T2)
- [x] T4 User write points incl. changeOwnPassword (needs T2)
- [x] T5 Seed script `seed` audit rows (needs T2; ask before wiping local DB)
- [ ] **Checkpoint 2:** full `./gradlew test`, spec success criteria ticked, diff review, Jorge commits

Skipped (decided): read API/export, field diffs, Envers, DB triggers, backfill, delete auditing.

## Architecture Decisions

- New package content in `infrastructure/audit/` next to `AuditableEntity` — the audit concern already lives there.
- `AuditService.record(entityType, entityId, operation)` called explicitly from services, next to the existing `eventLogger.emit(...)`. No JPA entity listener (inserting from inside a Hibernate flush is fragile).
- Username from the existing `AuditorAware<String>` bean, so `performed_by` always matches `created_by`/`updated_by`.
- `AuditLogEntry` does not extend `AuditableEntity` (rows are never updated).
- No foreign keys from `audit_log` to audited tables.
- ADR-006 records the decision; written first so review happens before code.

## Dependency Graph

```
T1 ADR-006 (docs only)
        │
T2 Tracer: V9 + AuditLogEntry/Repository/Service + DonationService + AuditLogTest
        │   (creates the table, the service and the test class everything else uses)
        ├──► T3 Expense + Donor write points
        ├──► T4 User write points (create, update, changeOwnPassword)
        └──► T5 Seed script audit rows (needs V9 table only)
```

T3, T4, T5 are independent of each other once T2 is green.

## Task List

### Phase 1: Decision + tracer bullet

#### T1: ADR-006 — append-only audit log
- **Acceptance:** `docs/decisions/ADR-006-audit-log.md` filled in all six template sections, status `Proposed`; index row added to `docs/decisions/README.md`; spec and idea doc link to it. Rejected alternatives (Envers, DB triggers, event-log sink, immutable ledger) recorded with reasons.
- **Verify:** Jorge reads and sets status to `Accepted`.
- **Files:** `docs/decisions/ADR-006-audit-log.md` (new), `docs/decisions/README.md`, `docs/specs/audit-log.md`.
- **Size:** S

#### T2: Tracer bullet — donation create/update audited end to end
- **Acceptance:**
  - `V9__create_audit_log.sql` creates the table + index exactly as in the spec.
  - Creating a donation via `POST /api/v1/donations` writes one `(DONATION, id, CREATE, <user>)` row.
  - Updating it writes `(DONATION, id, UPDATE, <user>)`; history keeps both, in order, across two different users.
  - `performed_by` equals the row's `updated_by` after an update.
  - A rejected create (400 validation error) writes no row.
- **Verify (TDD):** write `AuditLogTest` donation cases first → `./gradlew test --tests "com.example.donations.AuditLogTest"` fails → implement → passes. Then `./gradlew test` green.
- **Files:** `src/main/resources/db/migration/V9__create_audit_log.sql`, `infrastructure/audit/AuditLogEntry.kt`, `infrastructure/audit/AuditLogRepository.kt`, `infrastructure/audit/AuditService.kt`, `donation/DonationService.kt`, `src/test/kotlin/com/example/donations/AuditLogTest.kt`.
- **Size:** M (6 files, 4 new and small)

### Checkpoint 1: after T1–T2
- [x] `./gradlew test` green
- [x] Flyway applies V9 on the existing local Compose DB (`SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun` starts clean)
- [x] Review with Jorge before widening to other entities

### Phase 2: Remaining write points

#### T3: Expense + donor audited
- **Acceptance:** `createExpense`/`updateExpense` and `createDonor`/`updateDonor` each write one row with the right type and operation.
- **Verify:** add expense + donor cases to `AuditLogTest` first, see them fail, implement; `./gradlew test --tests "com.example.donations.AuditLogTest"` green.
- **Files:** `expense/ExpenseService.kt`, `donor/DonorService.kt`, `AuditLogTest.kt`.
- **Size:** S

#### T4: User changes audited
- **Acceptance:** `createUser` → `(USER, id, CREATE, admin)`; `updateUser` → `(USER, id, UPDATE, admin)`; successful `changeOwnPassword` → `(USER, id, UPDATE, <that user>)`; wrong current password → no row.
- **Verify:** add user cases to `AuditLogTest` first, see them fail, implement; targeted test run green.
- **Files:** `user/UserService.kt`, `AuditLogTest.kt`.
- **Size:** S

#### T5: Seed script writes `seed` audit rows
- **Acceptance:** before `COMMIT;`, three `INSERT INTO audit_log ... SELECT` statements (DONOR, DONATION, EXPENSE) with `performed_by = 'seed'`, limited to `created_by IS NULL` and `NOT EXISTS` an audit row. No user rows; `V3` untouched.
- **Verify (manual, local Compose DB):**
  1. Run the script against a fresh DB after V9 → `SELECT entity_type, count(*) FROM audit_log WHERE performed_by = 'seed' GROUP BY 1` returns DONOR 100, DONATION 606, EXPENSE 38 (confirm against script contents).
  2. Run only the audit section (9) again → counts unchanged (a full re-run aborts on duplicate `national_id`).
- **Files:** `src/test/resources/seed_test_data.sql`.
- **Size:** XS
- **Note:** a fresh DB means wiping local data — ask Jorge before resetting the Compose volume; alternative is a throwaway DB (`createdb` inside the container).

### Checkpoint 2: complete
- [x] `./gradlew test` green (whole suite)
- [x] All six spec success criteria ticked
- [ ] Diff review with Jorge; Jorge commits

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Testcontainers flake fails a whole class at context load | Med | Re-run once before investigating (known flake) |
| `saved.id!!` call order: audit before id assigned | Low | Call `record` after `repository.save(...)` returns, as the event logger does |
| Test isolation: `@DirtiesContext` per method is slow; audit rows from other tests leak | Low | Assert by `(entity_type, entity_id)` of the record created in the test, never on total counts |
| Seed `created_by IS NULL` matches non-seed rows | Low | Documented in spec; script is local-only, never run in prod |

## Open Questions

- None blocking. Retention period and auditor export format stay open in the spec.
