# Spec: Monthly balance timeseries

## Objective
Add `GET /api/v1/reports/balance/timeseries` so the frontend dashboard can plot income coverage
month over month in a single request.

- User: TREASURER / PASTOR viewing the dashboard.
- Behavior: aggregate donations and expenses into calendar-month buckets over a resolved date
  range, returning income, expenses, net balance and coverage ratio per bucket.
- Auth: inherits the class-level `@PreAuthorize("hasAnyRole('TREASURER', 'PASTOR')")` on
  `ReportController` (`src/main/kotlin/com/example/donations/report/ReportController.kt:14`).
- No event logging: read endpoints in this codebase do not emit events (`ReportService` does not
  use `EventLogger`, unlike the write services).

### Request

| Param | Required | Type | Notes |
|-------|----------|------|-------|
| `from` | yes | ISO date | No default. The caller states its own window. |
| `to` | no | ISO date | Defaults to today; clamped to today when in the future. |
| `groupBy` | yes | enum | `MONTH` is the only legal value today. |

`from` is required deliberately: a default of "start of the current year" collapses the chart to a
single month every January 1st, and only the caller knows whether the dashboard means "this year"
or "the last twelve months".

### Bounds resolution (in order)

1. `to` = min(`to` ?: today, today).
2. `from` = max(`from`, earliest transaction date across donations and expenses).
3. If no records exist at all, return `periods: []` with the range echoed from step 1.
4. If `from > to` after clamping, return 400. This also covers a `from` in the future.

The response echoes the **clamped** values.

There is no cap on range size. One church's ledger will not reach a size where ~320 zero-filled
monthly buckets matter, and any cap would be a guessed number.

### Bucketing

Calendar months, **clipped** to the resolved range — not snapped outward to whole months. The first
and last bucket may be partial, and `periodStart` / `periodEnd` state exactly what was measured.
Months inside the range with no rows are zero-filled (`totalIncome: 0`, `totalExpenses: 0`,
`netBalance: 0`, `coverageRatio: null`), never omitted.

### Response

    {
      "from": "2025-03-17",
      "to": "2026-08-26",
      "groupBy": "MONTH",
      "periods": [
        {
          "periodStart": "2025-03-17",
          "periodEnd": "2025-03-31",
          "totalIncome": 4100.00,
          "totalExpenses": 1950.00,
          "netBalance": 2150.00,
          "coverageRatio": 2.1026
        }
      ]
    }

Money fields reuse the `BalanceResponse` names (`ReportDtos.kt:33-39`) and the `DECIMAL(10,2)`
scale of the underlying columns (`V5__create_donations.sql:3`, `V6__create_expenses.sql:3`).

`coverageRatio` = `totalIncome / totalExpenses`, **scale 4, RoundingMode.HALF_UP**. Scale and
rounding are not optional: `8100.00 / 3900.00` is non-terminating and `BigDecimal.divide` throws
without them. Scale 4 keeps precision where it matters — a ratio hovering near the threshold reads
`1.00` at scale 2 whether it is 0,9951 or 1,0049. HALF_UP over HALF_EVEN because this is a
displayed indicator, not an accounting figure that must sum.

- `totalExpenses` = 0 → `coverageRatio` is `null` (coverage undefined, no expenses).
- `totalIncome` = 0 with real expenses → `coverageRatio` is `0.0000`. This is the alarm case and is
  a real number, not null.

The endpoint returns **numbers, not verdicts**: no `covered` flag, no trend classification. The
1,0 threshold is a product decision and lives with the chart.

## Acceptance criteria
1. `groupBy=MONTH` over a multi-month range returns one period per calendar month, ordered.
2. A month inside the range with no donations and no expenses is present and zero-filled, with
   `coverageRatio: null`.
3. `to` absent or in the future is clamped to today, and the clamped value is echoed in `to`.
4. `from` earlier than the first recorded transaction is clamped forward, and the clamped value is
   echoed in `from`.
5. First and last periods are clipped: `periodStart` / `periodEnd` equal the resolved range bounds,
   not the calendar month bounds.
6. `coverageRatio` is scale 4 HALF_UP; `null` when `totalExpenses` is 0; `0.0000` when
   `totalIncome` is 0 and expenses are non-zero.
7. `from > to` after clamping returns 400 (RFC 9457 ProblemDetail, per ADR-004).
8. An empty database returns `periods: []`, not 404 and not an error.
9. OPERATOR is denied; TREASURER and PASTOR are allowed.

## Tech Stack
Kotlin 2.2 / Java 24, Spring Boot 4.0.5 (Spring MVC + Spring Data JPA), PostgreSQL 18.3,
JUnit 5 + Testcontainers. No new dependencies.

## Commands
- Build:  `./gradlew build`
- Test (single):  `./gradlew test --tests "com.example.donations.FinancialReportsTest"`
- Test (unit only):  `./gradlew test --tests "com.example.donations.report.*"`
- Test (all):  `./gradlew test`
- Run (Testcontainers):  `./gradlew -PmainClass=com.example.donations.TestDonationsApplicationKt bootRun`

## Project Structure (touched)
src/main/kotlin/com/example/donations/report/
  ReportController.kt  → new @GetMapping("/balance/timeseries")
  ReportDtos.kt        → BalanceTimeseriesResponse + PeriodBalance + GroupBy enum, with @Schema
  ReportService.kt     → orchestration: clamp bounds, run queries, delegate to pure builder
  BalancePeriods.kt    → new file: pure bucketing/zero-fill/ratio function
src/main/kotlin/com/example/donations/donation/
  DonationRepository.kt → sumByMonthAndDateBetween + minDonationDate
src/main/kotlin/com/example/donations/expense/
  ExpenseRepository.kt  → sumByMonthAndDateBetween + minExpenseDate
src/test/kotlin/com/example/donations/
  FinancialReportsTest.kt      → 3 integration tests
  report/BalancePeriodsTest.kt → new: pure unit tests for the builder

## Code Style
Repository queries stay JPQL, matching every existing finder — no native SQL is introduced. HQL's
portable `year()` / `month()` functions avoid `date_trunc`, which is Postgres-specific and, since
its unit argument cannot be bound as a parameter, would require string interpolation into SQL:

    @Query(
        "SELECT year(d.donationDate), month(d.donationDate), SUM(d.amount) FROM Donation d " +
            "WHERE d.donationDate BETWEEN :from AND :to " +
            "GROUP BY year(d.donationDate), month(d.donationDate)"
    )
    fun sumByMonthAndDateBetween(from: LocalDate, to: LocalDate): List<Array<Any>>

    @Query("SELECT MIN(d.donationDate) FROM Donation d")
    fun minDonationDate(): LocalDate?

Mirror both on `ExpenseRepository` (`expenseDate`). The `BETWEEN` predicate still uses the existing
date indexes (`idx_donations_donation_date`, `idx_expenses_expense_date`).

The bucketing itself is extracted into a pure function with no Spring or JPA dependencies:

    internal fun buildBalancePeriods(
        from: LocalDate,
        to: LocalDate,
        incomeByMonth: Map<YearMonth, BigDecimal>,
        expensesByMonth: Map<YearMonth, BigDecimal>,
    ): List<BalanceTimeseriesResponse.PeriodBalance>

It iterates `YearMonth.from(from)..YearMonth.from(to)`, clips the first and last bucket to
`from` / `to`, zero-fills absent months, and computes `netBalance` and `coverageRatio`. This is a
single-call-site extraction made for testability, not reuse — a deliberate, narrow exception to
CLAUDE.md §2, justified in Testing Strategy below.

Ratio computation, exactly:

    private fun coverageRatio(income: BigDecimal, expenses: BigDecimal): BigDecimal? =
        if (expenses.signum() == 0) null
        else income.divide(expenses, 4, RoundingMode.HALF_UP)

`@Schema(description = …)` goes on `coverageRatio`, `periodStart` and `periodEnd` only. No other
DTO in this codebase carries Swagger annotations; this one does because these three fields have
semantics a field name cannot convey, and the descriptions flow into `openapi.yaml` and therefore
into the generated TypeScript client — the artifact frontend developers actually read.

## Testing Strategy
Two layers, because `FinancialReportsTest` is `@SpringBootTest` + Testcontainers with
`@DirtiesContext(AFTER_EACH_TEST_METHOD)` — every method rebuilds the context, so pushing nine
date-and-decimal cases through it is slow and needlessly exposed to container flake.

**Unit (`report/BalancePeriodsTest.kt`, plain JUnit 5, no Spring, no mocks)** — the builder is a
pure function, so these run in milliseconds:
- consecutive months bucketed and ordered;
- gap month zero-filled with `coverageRatio: null`;
- first and last buckets clipped to the range bounds;
- `coverageRatio` scale 4 HALF_UP on a non-terminating division;
- zero expenses → `null`; zero income with expenses → `0.0000`;
- single-day range;
- empty inputs over a real range → still zero-filled periods, never an empty list (the
  `periods: []` case is the service's no-records short-circuit, not the builder's).

**Integration (`FinancialReportsTest.kt`, existing style, `TestAuth` sessions)** — only what needs
a real database:
- end-to-end shape and totals across three seeded months (asserting `$.periods.length()`,
  `$.periods[0].coverageRatio`, `$.from`, `$.to`);
- clamping: request `from` before the first record and `to` at year end, assert both echoed values
  are clamped;
- authorization: OPERATOR forbidden, TREASURER allowed.

## Boundaries
- Always: run `./gradlew test` before reporting done; keep JPQL (no native SQL); keep existing
  report endpoints' behavior and response shapes untouched.
- Ask first: changing `defaultYearRange` (shared by four endpoints); adding dependencies; adding
  `WEEK` / `DAY` to the enum; DB migrations or new indexes.
- Never: return a sentinel instead of `null` for undefined coverage; snap edge buckets outward;
  omit zero-activity months; commit (Jorge commits himself).

## Success Criteria
All nine acceptance criteria pass; `./gradlew build` green; existing report tests unchanged and
passing; `/v3/api-docs` (dev profile) shows the new endpoint with its `@Schema` descriptions on
`coverageRatio`, `periodStart` and `periodEnd`.

## Open Questions
- None blocking. `WEEK` / `DAY` granularity is deferred pending a real caller and a decision on
  week boundaries (ISO Monday vs. a week ending Sunday, which is when offerings arrive).
