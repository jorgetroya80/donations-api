# Audit Log (who + when per change)

## Problem Statement

How might we let the church prove to an external auditor or the tax authority who created or changed each donation, expense, donor and user — and when — for every change, not just the latest one, without storing donor personal data?

## Recommended Direction

Add an append-only `audit_log` table, written by the application in the same transaction as the change. Each create or update of a donation, expense, donor or user inserts one row: entity type, entity id, operation, username, timestamp. No field values, no before/after diffs.

Today `AuditableEntity` (`src/main/kotlin/com/example/donations/infrastructure/audit/AuditableEntity.kt`) already stamps `created_by/at` and `updated_by/at` on every row, and this was verified working against the local DB (2026-11-23). The gap is that `updated_by/at` is overwritten on each edit, so only the last change survives. The audit log keeps the full sequence. The existing columns stay as they are — they remain the fast "current state" answer.

The write happens via a small `AuditService.record(entityType, entityId, operation)` called from the services at the same points that already call `eventLogger.emit(...)` (`DonationService`, `ExpenseService`, `DonorService`, `UserService`). The username comes from the same source as `AuditorAware` (security context, `system` fallback). Hibernate Envers and Postgres triggers were considered and rejected: Envers copies every field (national IDs, addresses) into shadow tables, conflicting with ADR-005; triggers can't see the logged-in user without per-transaction `SET LOCAL`, which fails silently when forgotten. Being a data-model change, this needs ADR-006.

## Key Assumptions to Validate

- [ ] Who + when (no values) satisfies the auditor — ask the treasurer what the auditor actually requested last time.
- [ ] Service create/update methods run in one transaction, so the audit row commits or rolls back with the change — check `@Transactional` on each service method.
- [ ] Every write path goes through a service (no repository writes from controllers or jobs) — grep for `repository.save` outside services.
- [ ] Volume stays trivial (hundreds of rows/year) — no partitioning or retention job needed.

## MVP Scope

In:
- Migration `V9__create_audit_log.sql`: `audit_log(id, entity_type, entity_id, operation, performed_by, performed_at)` + index on `(entity_type, entity_id)`.
- `AuditService.record(...)` + calls on create/update for donations, expenses, donors, and admin user actions.
- `src/test/resources/seed_test_data.sql`: append `INSERT INTO audit_log ... SELECT` statements (one per seeded table: donors, donations, expenses, users it creates) with `operation = 'CREATE'` and `performed_by = 'seed'`, so seeded rows have a visible origin. The initial admin user from `V3__seed_admin_user.sql` is excluded — it is an applied Flyway migration (immutable) and stays without an audit row.
- Integration tests: create + update a donation → two audit rows with correct user and order; rolled-back transaction → no audit row.
- ADR-006 + index row in `docs/decisions/README.md`.

Out: any read API — the auditor's answer is an SQL query run by the admin until someone asks for more.

## Not Doing (and Why)

- Before/after field values — not requested; storing them would copy donor personal data into a second place (ADR-005, GDPR minimization).
- Hibernate Envers — solves full history, a bigger problem than ours, and brings PII + Hibernate 7 compatibility risk.
- DB triggers — catch manual SQL edits, but user attribution is fragile; revisit only if manual Neon edits become common. The test seed script writes its own audit rows explicitly instead (see MVP Scope).
- Backfilling history for existing rows — old history is gone; at most, the current `created_by/at` values are the starting point.
- Audit read endpoint / UI / CSV export — no confirmed consumer yet.
- Login and read auditing — already covered by ADR-005 event logs; reads are not a stated requirement.

## Open Questions

- Does the auditor need a self-service export (endpoint/CSV), or is an admin-run query acceptable?
- Should deletes be audited? No delete endpoints exist today; if one is added, it must call `AuditService` too.
- Retention: keep forever, or align with the Spanish accounting retention period (6 years, Código de Comercio art. 30)?
