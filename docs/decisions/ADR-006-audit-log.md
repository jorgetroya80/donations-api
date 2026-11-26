# ADR-006: Append-only audit log of who and when, written by the application

## Status

Accepted

## Date

2026-11-23

## Context

The church must be able to show an external auditor or the tax authority **who created or
changed each donation, expense, donor and user account, and when — for every change**, not only
the most recent one.

What exists today:

- `AuditableEntity` (`src/main/kotlin/com/example/donations/infrastructure/audit/AuditableEntity.kt:25-39`)
  stamps `created_by`, `created_at`, `updated_by`, `updated_at` on every row, filled by Spring
  Data JPA auditing from the security context
  (`src/main/kotlin/com/example/donations/infrastructure/audit/AuditorAwareConfig.kt:14-19`).
  This was verified working against the local database on 2026-11-23. But `updated_by/at` is
  **overwritten on each edit**: if two people change a donation, only the second is recorded.
- ADR-005 event logs (`donation_create`, `donor_update`, `authz_admin`, …) carry the actor and
  entity id, but they go to stdout only. On Render's free tier (ADR-002) log retention is short
  and logs are not queryable, so they are not evidence an auditor can be given months later.

Constraints:

1. **Who and when is the requirement; field values are not.** No one has asked for
   before/after values, and storing them would copy donor personal data (names, national IDs,
   addresses) into a second place — the opposite of ADR-005's donor-data policy and of GDPR
   data minimization.
2. **The record must be trustworthy:** a change that commits must have an audit entry, and a
   change that rolls back must not.
3. **No budget for new infrastructure** (ADR-002) and no appetite for a new dependency for a
   small requirement.
4. All writes already go through `@Transactional` service methods; no repository is written
   to from anywhere else.

Driven by the spec [`docs/specs/audit-log.md`](../specs/audit-log.md) and plan
[`plans/audit-log.md`](../../plans/audit-log.md).

## Decision

Add an **append-only `audit_log` table, written by the application inside the same
transaction as the change it records**, holding identifiers only.

- **Schema** (`V9__create_audit_log.sql`): `id`, `entity_type` (`DONATION | EXPENSE | DONOR | USER`),
  `entity_id`, `operation` (`CREATE | UPDATE`), `performed_by` (username), `performed_by_id`
  (user id, null for `system`/`seed`), `performed_at`; `CHECK` constraints on `entity_type` and
  `operation`; index on `(entity_type, entity_id)`. **No foreign keys** to the audited tables, so the log
  outlives any row it refers to.
- **One explicit call per write.** `AuditService.record(entityType, entityId, operation)` is
  called from each service write method, next to the existing `eventLogger.emit(...)`:
  create and update in `DonationService`, `ExpenseService`, `DonorService`, and `UserService`
  (`createUser`, `updateUser`, and the success path of `changeOwnPassword`). Nine call sites.
- **Same transaction.** The call sits inside the `@Transactional` method, so the audit row
  commits or rolls back with the change.
- **One source for the username.** `AuditService` reads the existing `AuditorAware<String>`
  bean, so `audit_log.performed_by` matches `created_by`/`updated_by` on the row whenever the
  write changed a field (`system` when no one is authenticated). The user id is looked up by
  username in the same transaction and stored beside it, because usernames can be renamed
  (`UserService.updateUser`) and a freed name reused by a new account; the id keeps old entries
  pointing at the right person.
- **Identifiers only.** No field values, no diffs, no donor data — the table has no column
  that could hold them.
- **Append-only by code.** No update or delete path for `audit_log` exists in the application.
- **The existing `created_*/updated_*` columns stay** as the quick "current state" answer.
- **No read API.** An admin answers auditor questions with SQL until an export is asked for.
- **The local test seed script** (`src/test/resources/seed_test_data.sql`) writes its own audit
  rows with `performed_by = 'seed'`. The `V3` admin seed migration is applied and immutable,
  so the initial admin user has no audit row.

## Alternatives Considered

### Keep the current columns only
- Pros: zero work; already verified working.
- Cons: only the last change survives each edit; an auditor's "who changed this before?" has
  no answer.
- Rejected: fails the core requirement.

### Hibernate Envers
- Pros: the standard JPA answer; full row history and a query API for free; no call sites to
  remember.
- Cons: copies **every field** into `*_aud` shadow tables, including donor national IDs and
  addresses — a second store of personal data, against ADR-005 and GDPR minimization unless
  each sensitive field is excluded by hand. One shadow table per entity plus `revinfo`. New
  dependency, with unverified compatibility with Spring Boot 4 / Hibernate 7.
- Rejected: solves a bigger problem (full history of values) than the one we have, and brings
  personal-data and upgrade risk with it.

### PostgreSQL triggers
- Pros: cannot be bypassed — catches manual SQL edits in Neon, the seed script, anything.
- Cons: the database does not know the application user. Every transaction must run
  `SET LOCAL app.user = …`; any path that forgets produces a row with a blank user, silently.
  Logic split between Kotlin and PL/pgSQL, harder to test.
- Rejected for now: user attribution is the whole point, and this design makes it fragile.
  Revisit if manual production edits become common.

### Persist the ADR-005 events to a table
- Pros: no new call sites — the events already fire at the right moments.
- Cons: couples two concerns with different lifecycles. Events are a logging vocabulary that
  may change shape, include security events and failures, and are emitted outside the write's
  guarantees; an audit record must be stable and transactional.
- Rejected: an audit trail should not change because a log line was renamed.

### Immutable records (corrections as new entries)
- Pros: the accounting-ledger model auditors trust most; history is the data itself.
- Cons: a breaking change to the public API and the frontend — no more `PUT` for donations
  and expenses — and to how the treasurer works.
- Rejected: far more change than "who and when" requires.

### JPA entity listener writing the audit row
- Pros: no call sites in services; covers every entity extending `AuditableEntity` automatically.
- Cons: inserting a new entity from inside a Hibernate flush callback is fragile and poorly
  supported; the listener cannot tell a self-service password change from an admin edit if
  that ever matters.
- Rejected: nine explicit calls are easy to read and test; the hidden alternative is not.

## Consequences

- **Every change made through the application has a permanent who/when record**, ordered,
  per record. An auditor's question becomes one SQL query.
- **No personal data is added.** The table holds entity ids and operator usernames — the same
  identifiers ADR-005 already permits — so its privacy footprint is already accepted.
- **Changes made outside the application are invisible**: manual SQL in Neon, migrations,
  scripts other than the seed script. This is the price of rejecting triggers. Revisit trigger:
  the first time someone needs to fix production data by hand.
- **Writes during the V9 deploy cutover are not logged.** Render keeps the old instance serving
  until the new one passes its health check; writes handled by the old version in those seconds
  commit without an audit row. Deploy at a quiet time.
- **New write paths must remember to call `AuditService`.** There is no automatic coverage. A
  future delete endpoint, bulk import, or new entity must add its own call; the integration
  tests cover the nine existing ones only.
- **Existing rows have no history.** Data created before V9 is not backfilled; their current
  `created_*/updated_*` columns are the baseline.
- **The table grows forever.** At this application's volume (hundreds of writes a year) that
  is negligible. No retention period is defined yet; Spanish accounting rules require at least
  six years (Código de Comercio art. 30), so "keep forever" is compliant.
- **Append-only is a convention in code, not a database guarantee.** The single database role
  can still `UPDATE` or `DELETE` audit rows. Hardening this (a separate role, revoked
  privileges) is possible later if an auditor requires it.
- **`UPDATE` means "an update request was accepted", not "a field changed".** A PUT that
  changes nothing still writes an `UPDATE` row naming the submitter, while Hibernate skips the
  row write, so `updated_by` keeps the previous user. Skipping these would need per-service
  change detection; an auditor seeing an extra no-op entry is harmless. Pinned by a test.
- **Every write does one extra insert and one id lookup** (`users.username` is unique and
  indexed). Insignificant at this scale.
- **The audit package depends on the user package** (`AuditService` → `UserRepository`), the
  same direction `AuditorAwareConfig` already relies on through Spring Security.
- **A user renaming their own account mid-session** can be recorded with the old session username
  and a null id, if the new name is flushed before the lookup runs. Rare and untested; the
  username still identifies them.
