# PostgreSQL Query Optimization

## Context

Branch `feat-db-optimization`. Goal: analyze DB access and fix real query
inefficiencies. A full review of every repository, entity, and Flyway migration
(V1–V7) found the schema is mostly sound — date columns and the `donor_id` FK are
already indexed, report aggregates are server-side and date-bounded, and
`donations.donor` is correctly LAZY.

Two genuine problems remain. Everything else (filter indexes on enums/`active`/
audit columns, a separate `user_roles.user_id` index) is **rejected as
speculative**: the dataset is small (church donations app, low-thousands scale),
no query filters on those columns, and the composite PK `(user_id, role)` already
serves `WHERE user_id = X` via leftmost prefix. Per CLAUDE.md (simplicity, no
speculative work), we add indexes reactively later if `pg_stat_statements` flags a
real slow query.

## Problems being fixed

1. **Donor search is a full table scan.**
   `DonorRepository.kt:12` runs `LOWER(full_name) LIKE LOWER('%term%')` (and same
   on `national_id`). The leading wildcard makes any btree index unusable; every
   search scans all donors and computes `LOWER()` per row. This is the interactive
   search feature (#38) and its cost grows with donor count — the one
   scale-justified fix.

2. **`User.roles` EAGER → N+1.**
   `User.kt:30` uses `@ElementCollection(fetch = EAGER)`. Paginated `listUsers()`
   issues 1 query for the page + 1 `user_roles` query per user. Roles must stay
   loaded for the auth path, so the fix is batching, not LAZY.

## Changes

### 1. New Flyway migration — trigram index for donor search

New file `src/main/resources/db/migration/V8__add_donor_search_trgm_index.sql`
(migrations V1–V7 are immutable; this is the next version):

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_donors_full_name_trgm
    ON donors USING gin (LOWER(full_name) gin_trgm_ops);

CREATE INDEX idx_donors_national_id_trgm
    ON donors USING gin (LOWER(national_id) gin_trgm_ops);
```

- Index expression `LOWER(col) gin_trgm_ops` matches the query predicate
  `LOWER(col) LIKE ...` exactly, so the planner can use it for `%term%` substring
  matches. The existing `ESCAPE '\\'` clause stays valid.
- Plain `CREATE INDEX` (not `CONCURRENTLY`) is correct here — runs inside Flyway's
  transaction, fine at this table size.
- **No query rewrite needed** — `DonorRepository.kt` and `escapeLike` in
  `DonorService.kt:22` stay as-is.

### 2. Batch-fetch user roles

In `User.kt:30`, keep EAGER, add `@org.hibernate.annotations.BatchSize`:

```kotlin
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
@Column(name = "role")
@Enumerated(EnumType.STRING)
@org.hibernate.annotations.BatchSize(size = 100)
open var roles: Set<Role> = emptySet(),
```

Collapses the per-user role loads into batched `WHERE user_id IN (...)` queries
(1 + N → 1 + ⌈N/batch⌉; default page 20 → 2 queries total). No schema change, no
service change.

## Verification

1. `./gradlew flywayInfo` (or app boot with Docker Compose Postgres) — V8 applies
   cleanly; `ddl-auto: validate` still passes.
2. **Trgm index in use** — with seed data (test data seed script, #35), run in psql:
   ```sql
   EXPLAIN ANALYZE
   SELECT * FROM donors WHERE LOWER(full_name) LIKE LOWER('%test%');
   ```
   Expect a Bitmap Index Scan on `idx_donors_full_name_trgm`, not Seq Scan.
3. **Batched roles** — enable Hibernate SQL logging (or p6spy) and hit
   `GET /api/v1/users` with several users; confirm role loads are batched
   (`... in (?, ?, ...)`), not one select per user.
4. `./gradlew test` — full suite green (existing repository/service/controller and
   Testcontainers tests unchanged).

## Out of scope (deliberately not doing)

- Indexes on `donations.donation_type`, `expenses.category`, `active` flags, audit
  timestamps — no querying workload justifies them at current scale.
- Separate `user_roles.user_id` index — composite PK already covers it.
- Config tuning / autovacuum / monitoring (skill phases 5–7) — not warranted; add
  when production metrics demand it.
