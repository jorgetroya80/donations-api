# TODO — Monthly Balance Timeseries

Plan: [balance-timeseries.md](balance-timeseries.md) · Spec: [balance-timeseries-spec.md](../docs/balance-timeseries-spec.md) · ADR: [ADR-006](../docs/decisions/ADR-006-timeseries-response-envelope.md) (Proposed)

Nothing blocked. One stop-and-ask: if Hibernate rejects `year()` / `month()` in `GROUP BY`, the JPQL-only decision in ADR-006 breaks — raise it, do not switch to native SQL (Task 3).

## Phase 1: Rules in isolation

- [x] **Task 1 — Response DTOs and `GroupBy` enum** (XS · deps: none)
  - [x] `GroupBy` enum with single value `MONTH`
  - [x] `BalanceTimeseriesResponse` with nested `PeriodBalance`
  - [x] `coverageRatio` declared `BigDecimal?`
  - [x] Verify: `./gradlew compileKotlin` clean (full build at the phase checkpoint)

- [x] **Task 2 — Pure bucketing function and unit tests** (S · deps: 1)
  - [x] `BalancePeriods.kt` with `buildBalancePeriods` and ratio helper (scale 4, HALF_UP)
  - [x] Ordered consecutive months
  - [x] Gap month zero-filled with `coverageRatio: null`
  - [x] First/last buckets clipped to range bounds
  - [x] Zero expenses → `null`; zero income with expenses → `0.0000`
  - [x] Single-month range → one clipped period
  - [x] Verify: 8/8 green in `BalancePeriodsTest`
  - Spec fix: "empty inputs → empty list" contradicted the zero-fill rule; the `periods: []`
    case belongs to the service's no-records short-circuit (Task 4), not the builder.

### Checkpoint: Rules

- [ ] Unit tests green
- [ ] `./gradlew build` green
- [ ] No Spring or JPA import in `BalancePeriods.kt`

## Phase 2: End-to-end path

- [x] **Task 3 — Repository queries** (S · deps: none)
  - [x] `sumByMonthAndDateBetween` + `minDonationDate` on `DonationRepository`
  - [x] Same two on `ExpenseRepository` over `expenseDate`
  - [x] JPQL only — no `nativeQuery = true`
  - [x] Verify: context loads, `FinancialReportsTest` 12/12 green
  - [x] ✅ Risk retired: Hibernate 7 accepts bare `year()` / `month()` in `SELECT` and `GROUP BY`;
        queries validate at repository bootstrap. Rendering is proven by Task 5's integration test.
  - Note: grouped rows must be folded as `(row[0] as Number).toInt()` — PostgreSQL `EXTRACT`
    returns `numeric`, so the runtime type may be `BigDecimal` rather than `Integer`.

- [x] **Task 4 — Service orchestration and clamping** (S · deps: 1, 2, 3)
  - [x] `to` clamped to today; `from` clamped to earliest record; clamped values echoed
  - [x] Empty database → `periods: []` (both min-dates null; one empty table is not enough)
  - [x] `from > to` after clamping → 400 via `require(...)`, using the existing
        `IllegalArgumentException` handler — no new exception class
  - [x] `defaultYearRange` untouched
  - [x] Verify: `FinancialReportsTest` 15/15 green

- [x] **Task 5 — Controller endpoint and first integration test** (S · deps: 4)
  - [x] `@GetMapping("/balance/timeseries")`, `from` required, `to` optional, `groupBy` required
  - [x] Authorization inherited from the class-level `@PreAuthorize` — no method annotation
  - [x] Integration test: three seeded months → three ordered periods, correct sums and ratios
  - [x] Clamping test: over-wide range → both bounds clamped, no future periods (moved up from
        Task 6, it fell out of the same fixture)
  - [x] Verify: `FinancialReportsTest` 15/15 green
  - Two contract issues surfaced and resolved:
    - Missing/unparseable query params returned **500**, not 400 — the catch-all `Exception`
      handler claimed Spring's binding exceptions. Fixed with one handler for
      `ServletRequestBindingException` + `TypeMismatchException`. **Pre-existing, and it changes
      the status every endpoint returns for a bad param. Flagged for Jorge.**
    - `spring.jackson.default-property-inclusion: non_null` would have dropped `coverageRatio`
      from the JSON entirely for a zero-expense month. Forced an explicit `null` with a
      field-level `@JsonInclude(ALWAYS)`, matching the spec and the frontend contract.

### Checkpoint: End-to-end

- [ ] Integration tests green
- [ ] Live call over a seeded range returns correct buckets
- [ ] Existing report tests still pass
- [ ] **Review with Jorge before Phase 3**

## Phase 3: Edges, contract, docs

- [x] **Task 6 — Clamping, empty-DB and error-path tests** (XS · deps: 5)
  - [x] Over-wide range → both bounds clamped, no future periods (landed with Task 5)
  - [x] OPERATOR 403, PASTOR 200
  - [x] Future `from` → 400
  - [x] Empty ledger → `periods: []` — needed its own class, `BalanceTimeseriesEmptyDataTest`,
        since `FinancialReportsTest` seeds donations and expenses in `@BeforeEach`
  - [x] Verify: `FinancialReportsTest` 18/18, `BalanceTimeseriesEmptyDataTest` 1/1
  - Gotcha for any future test class: the context cache key ignores `@DirtiesContext`, so a class
    without it hands its mutated database to the next class — and `TestAuth.loginAsAdmin` rotates
    the admin password, so the next class's login fails with a 401 that looks like a flake.

- [x] **Task 7 — `@Schema` descriptions** (XS · deps: 5)
  - [x] Descriptions on `coverageRatio`, `periodStart`, `periodEnd` only
  - [x] Verify: asserted in `OpenApiTest` rather than checked by hand, so a future edit that
        drops them fails the build

## Phase 4: Time zone correctness (added 2026-08-26, approved on this branch)

Driver: `LocalDate.now()` reads the JVM default zone, which is UTC on the deploy target while the
church is in Europe/Madrid. For the first 1–2 hours of each local day the server's "today" is
still yesterday, so a donation dated today is clamped out of the series and looks lost. The
Dec 31 → Jan 1 rollover is the worst case. This also retires the suite's 2027 rot.

- [x] **Task 8 — Inject a `Clock` with an explicit zone** (M · deps: 5)
  - [x] `TimeConfig` bean: `Clock.system(ZoneId.of(app.timezone))`, default `Europe/Madrid`
  - [x] `defaultYearRange` takes the clock; `DonationService`, `ExpenseService`, `ReportService`
        inject it
  - [x] Bounds arithmetic extracted to a pure `resolveTimeseriesBounds(from, to, today, earliest)`
  - [x] Unit tests pin the boundary: `today = 2026-12-31` vs `2027-01-01`, each run through
        `buildBalancePeriods` so a dropped or duplicated edge month fails too
  - [x] Zone asserted in `DeployPortBindingTest` — against the literal zone, not
        `ZoneId.systemDefault()`, which would pass for the wrong reason on a Madrid laptop
  - [x] Verify: `BalancePeriodsTest` 16/16, `DeployPortBindingTest` 4/4
  - Side effect, left alone deliberately: `LoginAttemptService` declares
    `clock: Clock = Clock.systemUTC()` as a Kotlin default. With a `Clock` bean now present,
    Spring injects the Madrid clock and that default becomes unreachable in production. Harmless
    — lockout windows are measured with `Instant`/`Duration`, which are zone-independent — but
    it is a real behaviour change outside this task's scope. **Flagged for Jorge.**

- [x] **Task 9 — Pin the integration fixtures to a fixed clock** (S · deps: 8)
  - [x] `FixedClockConfiguration` pins today to 2026-08-26 in Europe/Madrid; distinct bean name
        plus `@Primary`, since a same-name bean would be a definition clash Boot rejects
  - [x] The four default-range tests in `FinancialReportsTest` now survive 2027-01-01
  - [x] Verify: `FinancialReportsTest` 18/18, `BalanceTimeseriesEmptyDataTest` 1/1

### Checkpoint: Complete

- [x] All nine spec acceptance criteria pass
- [x] `./gradlew build` green — 233 tests, 0 failures
- [x] `/v3/api-docs` shows the endpoint and the three field descriptions
- [x] Committed per task on `feat/balance-timeseries` (Jorge approved commits on a branch for
      this run only; nothing pushed, `main` untouched)
