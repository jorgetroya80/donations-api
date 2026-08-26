# TODO — Monthly Balance Timeseries

Plan: [balance-timeseries.md](balance-timeseries.md) · Spec: [balance-timeseries-spec.md](../docs/balance-timeseries-spec.md) · ADR: [ADR-006](../docs/decisions/ADR-006-timeseries-response-envelope.md) (Proposed)

Nothing blocked. One stop-and-ask: if Hibernate rejects `year()` / `month()` in `GROUP BY`, the JPQL-only decision in ADR-006 breaks — raise it, do not switch to native SQL (Task 3).

## Phase 1: Rules in isolation

- [ ] **Task 1 — Response DTOs and `GroupBy` enum** (XS · deps: none)
  - [ ] `GroupBy` enum with single value `MONTH`
  - [ ] `BalanceTimeseriesResponse` with nested `PeriodBalance`
  - [ ] `coverageRatio` declared `BigDecimal?`
  - [ ] Verify: `./gradlew build`

- [ ] **Task 2 — Pure bucketing function and unit tests** (S · deps: 1)
  - [ ] `BalancePeriods.kt` with `buildBalancePeriods` and ratio helper (scale 4, HALF_UP)
  - [ ] Ordered consecutive months
  - [ ] Gap month zero-filled with `coverageRatio: null`
  - [ ] First/last buckets clipped to range bounds
  - [ ] Zero expenses → `null`; zero income with expenses → `0.0000`
  - [ ] Single-month range → one clipped period
  - [ ] Verify: `./gradlew test --tests "com.example.donations.report.BalancePeriodsTest"`

### Checkpoint: Rules

- [ ] Unit tests green
- [ ] `./gradlew build` green
- [ ] No Spring or JPA import in `BalancePeriods.kt`

## Phase 2: End-to-end path

- [ ] **Task 3 — Repository queries** (S · deps: none)
  - [ ] `sumByMonthAndDateBetween` + `minDonationDate` on `DonationRepository`
  - [ ] Same two on `ExpenseRepository` over `expenseDate`
  - [ ] JPQL only — no `nativeQuery = true`
  - [ ] Verify: context loads (`./gradlew test --tests "…FinancialReportsTest"`)
  - [ ] ⚠ If Hibernate rejects `year()` / `month()` in `GROUP BY`, stop and raise — do not switch
        to native SQL unilaterally

- [ ] **Task 4 — Service orchestration and clamping** (S · deps: 1, 2, 3)
  - [ ] `to` clamped to today; `from` clamped to earliest record; clamped values echoed
  - [ ] Empty database → `periods: []`
  - [ ] `from > to` after clamping → 400 ProblemDetail
  - [ ] `defaultYearRange` untouched
  - [ ] Verify: `./gradlew build`

- [ ] **Task 5 — Controller endpoint and first integration test** (S · deps: 4)
  - [ ] `@GetMapping("/balance/timeseries")`, `from` required, `to` optional, `groupBy` required
  - [ ] Authorization inherited from the class-level `@PreAuthorize` — no method annotation
  - [ ] Integration test: three seeded months → three ordered periods, correct sums and ratios
  - [ ] Verify: `./gradlew test --tests "com.example.donations.FinancialReportsTest"`

### Checkpoint: End-to-end

- [ ] Integration tests green
- [ ] Live call over a seeded range returns correct buckets
- [ ] Existing report tests still pass
- [ ] **Review with Jorge before Phase 3**

## Phase 3: Edges, contract, docs

- [ ] **Task 6 — Clamping, empty-DB and error-path tests** (XS · deps: 5)
  - [ ] Over-wide range → both bounds clamped, no future periods
  - [ ] OPERATOR 403, PASTOR 200
  - [ ] Future `from` → 400
  - [ ] Verify: `./gradlew test --tests "com.example.donations.FinancialReportsTest"`
        (re-run once on a context-load failure — known flake)

- [ ] **Task 7 — `@Schema` descriptions** (XS · deps: 5)
  - [ ] Descriptions on `coverageRatio`, `periodStart`, `periodEnd` only
  - [ ] Verify: descriptions visible in `/v3/api-docs` under the dev profile

### Checkpoint: Complete

- [ ] All nine spec acceptance criteria pass
- [ ] `./gradlew build` green
- [ ] `/v3/api-docs` shows endpoint with field descriptions
- [ ] Report to Jorge — **do not commit**
