# Implementation Plan: Monthly balance timeseries

## Overview

Add `GET /api/v1/reports/balance/timeseries`, returning income, expenses, net balance and coverage
ratio per calendar month in a single request, so the donations-frontend dashboard can plot whether
income still covers expenses month over month.

Implements [balance-timeseries-spec.md](../docs/balance-timeseries-spec.md) under
[ADR-006](../docs/decisions/ADR-006-timeseries-response-envelope.md) (Proposed). Context, rationale
and the frontend rendering contract live in
[docs/balance-timeseries.md](../docs/balance-timeseries.md). Task checklist:
[balance-timeseries-todo.md](balance-timeseries-todo.md).

Scope is backend only. The by-type timeseries and `WEEK` / `DAY` granularity are deferred.

## Architecture Decisions

Restated from ADR-006 and the spec, because they constrain every task below:

- **JPQL only, no native SQL.** Month bucketing uses HQL's portable `year()` / `month()` rather
  than Postgres `date_trunc`, whose unit argument cannot be bound as a parameter and would require
  interpolating a string into SQL.
- **The bucketing logic is a pure function.** `buildBalancePeriods(from, to, incomeByMonth,
  expensesByMonth)` has no Spring or JPA dependency, so every date-and-decimal rule is unit-testable
  without a database. A single-call-site extraction made for testability, not reuse.
- **Bounds are clamped, then echoed.** `to` clamps back to today, `from` clamps forward to the
  earliest recorded transaction, and the response reports the clamped values.
- **Edge buckets are clipped, not snapped.** `periodStart` / `periodEnd` state exactly what was
  measured, so a consumer can detect a partial month.
- **Numbers, not verdicts.** `coverageRatio` (scale 4, HALF_UP, `null` when expenses are zero) is
  returned; the 1,0 threshold is the consumer's.

## Dependency Graph

```
ReportDtos.kt (GroupBy, BalanceTimeseriesResponse, PeriodBalance)
    │
    ├── BalancePeriods.kt  (pure bucketing + ratio)
    │       │
    │       └── BalancePeriodsTest.kt  (plain JUnit, no Spring)
    │
    └── ReportService.balanceTimeseries
            │
            ├── DonationRepository.sumByMonthAndDateBetween / minDonationDate
            ├── ExpenseRepository.sumByMonthAndDateBetween / minExpenseDate
            │
            └── ReportController @GetMapping("/balance/timeseries")
                    │
                    └── FinancialReportsTest  (integration, Testcontainers)
                            │
                            └── @Schema descriptions → openapi.yaml → generated TS client
```

Foundation first: the DTOs and the pure builder hold every rule that is easy to get wrong, so they
are built and unit-tested before anything touches Spring or a database.

## Task List

### Phase 1: Rules in isolation

- [ ] Task 1: Response DTOs and `GroupBy` enum
- [ ] Task 2: Pure bucketing function and its unit tests

### Checkpoint: Rules

- [ ] `./gradlew test --tests "com.example.donations.report.BalancePeriodsTest"` green
- [ ] Every acceptance criterion that is pure date-and-decimal logic (2, 5, 6) is covered by a
      unit test
- [ ] `./gradlew build` compiles with the new files unused by production code so far

### Phase 2: End-to-end path

- [ ] Task 3: Repository grouped-sum and min-date queries
- [ ] Task 4: Service orchestration and bounds clamping
- [ ] Task 5: Controller endpoint and first integration test

### Checkpoint: End-to-end

- [ ] `./gradlew test --tests "com.example.donations.FinancialReportsTest"` green
- [ ] A live call over a three-month seeded range returns correctly bucketed periods
- [ ] Existing report endpoints unchanged and their tests still pass
- [ ] Review with Jorge before proceeding

### Phase 3: Edges, contract, docs

- [ ] Task 6: Clamping, empty-database and error-path integration tests
- [ ] Task 7: `@Schema` descriptions on the three non-obvious fields

### Checkpoint: Complete

- [ ] All nine acceptance criteria in the spec pass
- [ ] `./gradlew build` green
- [ ] `/v3/api-docs` shows the endpoint with field descriptions
- [ ] Report to Jorge — do not commit

---

## Task 1: Response DTOs and `GroupBy` enum

**Description:** Add the response envelope types to `ReportDtos.kt`: a `GroupBy` enum whose only
value is `MONTH`, a `BalanceTimeseriesResponse` carrying `from`, `to`, `groupBy` and `periods`, and
a nested `PeriodBalance` with `periodStart`, `periodEnd`, `totalIncome`, `totalExpenses`,
`netBalance` and a nullable `coverageRatio`. Money field names reuse `BalanceResponse`
(`ReportDtos.kt:33-39`) so the per-period form of the endpoint reads the same as the range form.
No annotations in this task — descriptions come in Task 7.

**Acceptance criteria:**
- [ ] `GroupBy` enum exists with the single value `MONTH`.
- [ ] `BalanceTimeseriesResponse` matches the response shape in the spec, with `PeriodBalance`
      nested inside it (mirroring how `TypeTotal` and `CategoryTotal` nest today).
- [ ] `coverageRatio` is declared `BigDecimal?`.

**Verification:**
- [ ] Build succeeds: `./gradlew build`
- [ ] Manual check: field names match `BalanceResponse` exactly for the three money fields.

**Dependencies:** None

**Files likely touched:**
- `src/main/kotlin/com/example/donations/report/ReportDtos.kt`

**Estimated scope:** XS (1 file)

---

## Task 2: Pure bucketing function and its unit tests

**Description:** Add `src/main/kotlin/com/example/donations/report/BalancePeriods.kt` containing
`buildBalancePeriods(from, to, incomeByMonth, expensesByMonth)` and a private `coverageRatio`
helper. The function iterates from `YearMonth.from(from)` to `YearMonth.from(to)`, clips the first
and last bucket to the range bounds, zero-fills months absent from both maps, and computes
`netBalance` and `coverageRatio` (scale 4, `RoundingMode.HALF_UP`; `null` when expenses are zero).
Then add `src/test/kotlin/com/example/donations/report/BalancePeriodsTest.kt` — plain JUnit 5, no
Spring context, no Testcontainers, no mocks.

This is the highest-risk task and it runs first: every subtle rule in the design lives here, and
here it costs milliseconds to test rather than a container restart per case.

**Acceptance criteria:**
- [ ] Consecutive months are returned in order, one period per calendar month in range.
- [ ] A month present in neither map is zero-filled with `coverageRatio: null`, not omitted.
- [ ] First and last buckets are clipped to `from` / `to`; interior buckets span whole months.
- [ ] `coverageRatio` is scale 4 HALF_UP (`8100.00 / 3900.00` → `2.0769`); `null` when expenses are
      zero; `0.0000` when income is zero and expenses are not.
- [ ] A range inside a single month yields exactly one clipped period.

**Verification:**
- [ ] Tests pass: `./gradlew test --tests "com.example.donations.report.BalancePeriodsTest"`
- [ ] Build succeeds: `./gradlew build`
- [ ] Manual check: no Spring or JPA import appears in either new file.

**Dependencies:** Task 1

**Files likely touched:**
- `src/main/kotlin/com/example/donations/report/BalancePeriods.kt`
- `src/test/kotlin/com/example/donations/report/BalancePeriodsTest.kt`

**Estimated scope:** S (2 files)

---

## Task 3: Repository grouped-sum and min-date queries

**Description:** Add to `DonationRepository` a `sumByMonthAndDateBetween(from, to):
List<Array<Any>>` grouping with `year()` / `month()` over `donationDate`, and a
`minDonationDate(): LocalDate?`. Mirror both on `ExpenseRepository` over `expenseDate`. Follow the
existing `@Query` JPQL style (`ExpenseRepository.kt:18-28`). The `BETWEEN` predicate keeps using
`idx_donations_donation_date` and `idx_expenses_expense_date`.

**Acceptance criteria:**
- [ ] Both repositories expose a monthly grouped-sum finder and a min-date finder.
- [ ] Queries are JPQL — no `nativeQuery = true` anywhere.
- [ ] Min-date finders are nullable-typed, since an empty table returns `null`.

**Acceptance verification note:** grouped-sum queries need a database to prove out, so their
correctness is asserted in Task 5's integration test rather than here; this task's bar is that the
application context starts, which is where an invalid JPQL query fails.

**Verification:**
- [ ] Build succeeds: `./gradlew build`
- [ ] Tests pass: `./gradlew test --tests "com.example.donations.FinancialReportsTest"` — an
      invalid query surfaces as a context-load failure of the whole class.
- [ ] Manual check: confirm Hibernate accepts `year()` / `month()` in the `GROUP BY`. If it does
      not, stop and raise it (see Risks) rather than switching to native SQL.

**Dependencies:** None

**Files likely touched:**
- `src/main/kotlin/com/example/donations/donation/DonationRepository.kt`
- `src/main/kotlin/com/example/donations/expense/ExpenseRepository.kt`

**Estimated scope:** S (2 files)

---

## Task 4: Service orchestration and bounds clamping

**Description:** Add `ReportService.balanceTimeseries(from, to, groupBy)`. Resolve bounds in the
spec's order: `to` = min(`to` ?: today, today); `from` = max(`from`, earliest of the two min-dates);
when both tables are empty return the envelope with `periods: []`; when `from > to` after clamping
throw so the handler renders a 400 ProblemDetail. Fold each repository's rows into
`Map<YearMonth, BigDecimal>` and delegate to `buildBalancePeriods`. Bounds resolution is deliberately
*not* routed through `defaultYearRange` — that function is shared by four endpoints and its
contract is "the current calendar year", which is neither half of what this endpoint needs.

**Acceptance criteria:**
- [ ] `to` absent or in the future resolves to today; `from` earlier than the first record resolves
      to that record's date; both resolved values are what the response echoes.
- [ ] Empty database yields `periods: []` and no exception.
- [ ] `from > to` after clamping raises an error that maps to a 400 ProblemDetail (ADR-004).
- [ ] `defaultYearRange` and the four endpoints using it are untouched.

**Verification:**
- [ ] Build succeeds: `./gradlew build`
- [ ] Tests pass: `./gradlew test --tests "com.example.donations.FinancialReportsTest"`
- [ ] Manual check: row-to-map folding handles the numeric types Hibernate returns for
      `year()` / `month()` (see Risks).

**Dependencies:** Tasks 1, 2, 3

**Files likely touched:**
- `src/main/kotlin/com/example/donations/report/ReportService.kt`
- possibly `src/main/kotlin/com/example/donations/infrastructure/error/` — only if no existing
  exception type maps to 400; check before adding one.

**Estimated scope:** S (1-2 files)

---

## Task 5: Controller endpoint and first integration test

**Description:** Add `@GetMapping("/balance/timeseries")` to `ReportController` with `from`
required, `to` optional and `groupBy` required, each `@DateTimeFormat(iso = ISO.DATE)` where
applicable, matching the sibling endpoints. Authorization is inherited from the class-level
`@PreAuthorize("hasAnyRole('TREASURER', 'PASTOR')")` (`ReportController.kt:14`) — no method-level
annotation. Then add the first integration test to `FinancialReportsTest`: seed donations and
expenses across three months using the existing `createDonation` / `createExpense` helpers and
assert the end-to-end shape and totals.

**Acceptance criteria:**
- [ ] Endpoint returns 200 with the spec's response shape for a TREASURER session.
- [ ] Three seeded months produce three ordered periods with correct sums and ratios.
- [ ] Omitting `from` returns 400 (required parameter).

**Verification:**
- [ ] Tests pass: `./gradlew test --tests "com.example.donations.FinancialReportsTest"`
- [ ] Build succeeds: `./gradlew build`
- [ ] Manual check: run `TestDonationsApplication` and call
      `GET /api/v1/reports/balance/timeseries?from=2026-01-01&groupBy=MONTH` with a TREASURER
      session.

**Dependencies:** Task 4

**Files likely touched:**
- `src/main/kotlin/com/example/donations/report/ReportController.kt`
- `src/test/kotlin/com/example/donations/FinancialReportsTest.kt`

**Estimated scope:** S (2 files)

---

## Task 6: Clamping, empty-database and error-path integration tests

**Description:** Add the remaining integration tests to `FinancialReportsTest`: a clamping test
requesting a range wider than reality on both ends and asserting both echoed bounds; an
authorization test asserting OPERATOR is forbidden and PASTOR is allowed; and the error path for
`from` after `to`. Keep the count low — every method rebuilds the context under
`@DirtiesContext(AFTER_EACH_TEST_METHOD)`, and the pure rules are already covered by Task 2.

**Acceptance criteria:**
- [ ] Requesting `from` before the first record and `to` at year end returns both bounds clamped in
      the echoed range, with no future periods.
- [ ] OPERATOR receives 403; PASTOR receives 200.
- [ ] `from` in the future returns 400 (it clamps to `from > to`).

**Verification:**
- [ ] Tests pass: `./gradlew test --tests "com.example.donations.FinancialReportsTest"`
- [ ] Build succeeds: `./gradlew build`
- [ ] Manual check: on a re-run failure, re-run once before investigating — this class has a known
      Testcontainers context-load flake.

**Dependencies:** Task 5

**Files likely touched:**
- `src/test/kotlin/com/example/donations/FinancialReportsTest.kt`

**Estimated scope:** XS (1 file)

---

## Task 7: `@Schema` descriptions on the three non-obvious fields

**Description:** Annotate `coverageRatio`, `periodStart` and `periodEnd` on `PeriodBalance` with
`@Schema(description = …)` so the semantics reach `openapi.yaml` and therefore the generated
TypeScript client, which is the artifact frontend developers actually read. `coverageRatio`: null
means no expenses in the period, render as a gap rather than zero. `periodStart` / `periodEnd`:
clipped to the queried range, so the final period may be incomplete. No other DTO in the codebase
carries Swagger annotations — this is a deliberate, documented exception, not a new house style.

**Acceptance criteria:**
- [ ] The three fields carry descriptions; no other DTO is annotated.
- [ ] Descriptions appear in `/v3/api-docs` under the dev profile.

**Verification:**
- [ ] Build succeeds: `./gradlew build`
- [ ] Manual check: run `TestDonationsApplication` with the dev profile, open `/v3/api-docs`, and
      confirm the three descriptions are present on the timeseries period schema.

**Dependencies:** Task 5

**Estimated scope:** XS (1 file)

**Files likely touched:**
- `src/main/kotlin/com/example/donations/report/ReportDtos.kt`

---

---

## Task 8: Inject a `Clock` with an explicit zone

**Description:** `LocalDate.now()` resolves against the JVM default zone. On the deploy target
that is UTC, while the church is in Europe/Madrid — so for the first one to two hours of every
local day the server's "today" is still yesterday. A donation dated today is then clamped out of
the series and reads as lost data, and the effect is most visible at the Dec 31 → Jan 1 rollover.

Add a `Clock` bean built from an explicit, configurable zone; thread it through `defaultYearRange`
and the three services that call it; and extract the timeseries bounds arithmetic into a pure
`resolveTimeseriesBounds(from, to, today, earliest)` so the rollover can be tested exactly rather
than inferred.

This is a production change made for correctness, not for test convenience — "today" is genuine
business input to this endpoint, and clamping to it *is* the feature.

**Acceptance criteria:**
- [ ] The clock's zone comes from `app.timezone` (default `Europe/Madrid`), never the JVM default.
- [ ] `defaultYearRange` derives the year from the injected clock; the four endpoints using it
      behave identically under the system clock.
- [ ] `resolveTimeseriesBounds` is pure and takes `today` as a parameter.
- [ ] Unit tests pin both sides of the rollover: `today = 2026-12-31` and `today = 2027-01-01`.

**Verification:**
- [ ] Tests pass: `./gradlew test --tests "com.example.donations.report.BalancePeriodsTest"`
- [ ] Build succeeds: `./gradlew build`

**Dependencies:** Task 5

**Files likely touched:**
- `src/main/kotlin/com/example/donations/infrastructure/config/TimeConfig.kt` (new)
- `src/main/kotlin/com/example/donations/infrastructure/DateRanges.kt`
- `src/main/kotlin/com/example/donations/report/BalancePeriods.kt`, `ReportService.kt`
- `src/main/kotlin/com/example/donations/donation/DonationService.kt`,
  `src/main/kotlin/com/example/donations/expense/ExpenseService.kt`
- `src/main/resources/application.yaml`

**Estimated scope:** M (5+ files, each touched narrowly)

---

## Task 9: Pin the integration fixtures to a fixed clock

**Description:** `FinancialReportsTest` seeds data at fixed 2026 dates but four of its tests send
no `from`/`to`, so they fall through `defaultYearRange` to the current year — on 2027-01-01 the
seeded rows fall outside the default window and the asserted totals go to zero. With Task 8's
clock injectable, a fixed test clock removes the rot without rewriting the seed data.

**Acceptance criteria:**
- [ ] Integration tests run against a fixed clock in Europe/Madrid, not the system clock.
- [ ] The four default-range tests would still pass on 2027-01-01.
- [ ] New test classes carry `@DirtiesContext` (the context cache key ignores it, and
      `TestAuth.loginAsAdmin` rotates the admin password — see Task 6's note).

**Verification:**
- [ ] Tests pass: `./gradlew build`

**Dependencies:** Task 8

**Files likely touched:**
- `src/test/kotlin/com/example/donations/FinancialReportsTest.kt`
- `src/test/kotlin/com/example/donations/BalanceTimeseriesEmptyDataTest.kt`
- a shared fixed-clock test configuration

**Estimated scope:** S

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Hibernate rejects `year()` / `month()` in `GROUP BY`, or requires `FUNCTION('…')` syntax | High — the whole JPQL-only decision rests on it | Prove it in Task 3, before the service and controller are built. If it genuinely cannot work, raise it: the fallback is native `date_trunc` with a hardcoded unit literal, which contradicts ADR-006 and is Jorge's call, not a silent substitution. |
| Hibernate returns `year()` / `month()` as `Integer`, `Long` or `BigDecimal` depending on version | Medium — a bad cast throws at runtime, not compile time | Fold rows defensively via `Number.toInt()` rather than casting to a specific type; cover with the Task 5 integration test. |
| `SUM()` over an empty group returns `null` | Medium — a null-unsafe fold NPEs | Existing code already handles this (`?: BigDecimal.ZERO` in `ReportService.kt`); mirror it. |
| Testcontainers context-load flake fails the whole `FinancialReportsTest` class | Low — false alarm, wasted investigation | Known issue: re-run the class once before investigating. |
| Task 4 finds no existing exception type mapping to 400 | Low | Check `GlobalExceptionHandler` first; prefer an existing mechanism over adding a new exception class. |
| Bucketing bugs slip through because integration tests are few | Medium | Deliberate trade: Task 2 covers the rules exhaustively at unit level; integration tests only prove wiring and SQL. |

## Open Questions

- None blocking. `WEEK` / `DAY` granularity stays deferred pending a real caller and a decision on
  week boundaries (ISO Monday versus a week ending Sunday, which is when offerings arrive).
- Worth confirming during Task 5: an unrecognised `groupBy` value should surface as a 400
  ProblemDetail rather than a 500. If Spring's enum binding produces something else, it is a
  one-line fix in `GlobalExceptionHandler`, not a design change.

## Definition of Done

- All nine acceptance criteria in [`docs/balance-timeseries-spec.md`](../docs/balance-timeseries-spec.md) pass.
- `./gradlew build` green; existing report tests unchanged and passing.
- `/v3/api-docs` lists the endpoint with the three field descriptions.
- Report to Jorge. **Do not commit** — Jorge commits himself.
