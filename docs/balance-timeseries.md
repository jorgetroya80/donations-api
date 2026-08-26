# Plan: Add `GET /api/v1/reports/balance/timeseries`

## Context

The donations-frontend dashboard renders "Ingresos y gastos por mes" — two lines, income and
expenses, over the last months. Both lines move, so the chart shows *levels* but not the question
the treasurer is actually asking: **does income still cover expenses, and is the cushion
shrinking?** Net euros do not answer it either — a cushion can grow in absolute terms while
coverage thins (8.100 / 3.900 = 2,08 with a 4.200 net; later 12.000 / 7.000 = 1,71 with a 5.000
net: bigger cushion, worse coverage).

The answer is a single series: **coverage ratio = income / expenses per month**, plotted against a
reference line at 1,0. One line, one question, no mental subtraction.

Nothing in the API buckets by month today. `/api/v1/reports/balance`
(`src/main/kotlin/com/example/donations/report/ReportService.kt:70-84`) returns one total per
range, so a monthly series currently costs one request per month.

Outcome: a new `GET /api/v1/reports/balance/timeseries` returning income, expenses, net and
coverage ratio per month in one request. The response envelope, the bounds clamping and the
bucketing rules are a convention for all future report timeseries endpoints and are recorded in
[ADR-006](decisions/ADR-006-timeseries-response-envelope.md) (status: Proposed).

Deferred, not in this scope: `/api/v1/reports/donations/timeseries` for the dashboard's second
chart ("Donaciones por tipo y mes") — same envelope, `totalsByType` + `grandTotal` per period.
`WEEK` / `DAY` granularity — see ADR-006 Consequences.

---

## Specification

The full specification lives in [`balance-timeseries-spec.md`](balance-timeseries-spec.md):
objective, request parameters, bounds resolution, bucketing, response shape, acceptance
criteria, code style, testing strategy and boundaries. Copy it to `SPEC.md` at the repo root at
implement time.

---

## Implementation steps (after spec approved)

1. `DonationRepository.kt` → add `sumByMonthAndDateBetween` and `minDonationDate` per the JPQL in
   [`balance-timeseries-spec.md`](balance-timeseries-spec.md) (Code Style). → verify: compiles.
2. `ExpenseRepository.kt` → mirror both on `expenseDate`. → verify: compiles.
3. `ReportDtos.kt` → add `GroupBy` enum (`MONTH`), `BalanceTimeseriesResponse` with nested
   `PeriodBalance`, `@Schema` descriptions on the three fields. → verify: compiles.
4. `report/BalancePeriods.kt` → new file with `buildBalancePeriods` and the ratio helper.
   → verify: compiles.
5. `report/BalancePeriodsTest.kt` → the eight unit cases. → verify:
   `./gradlew test --tests "com.example.donations.report.BalancePeriodsTest"`.
6. `ReportService.kt` → `balanceTimeseries(from, to, groupBy)`: clamp bounds (min dates from both
   repositories, `to` clamped to today), 400 on `from > to`, run both grouped queries, fold rows
   into `Map<YearMonth, BigDecimal>`, delegate to `buildBalancePeriods`. → verify: compiles.
7. `ReportController.kt` → `@GetMapping("/balance/timeseries")` with `from` required, `to`
   optional, `groupBy` required, all `@DateTimeFormat(iso = ISO.DATE)` as on the sibling endpoints.
   → verify: compiles.
8. `FinancialReportsTest.kt` → the three integration tests. → verify:
   `./gradlew test --tests "com.example.donations.FinancialReportsTest"`.
9. Full `./gradlew build`. → verify: green. Report to Jorge (do not commit).

## Verification (end-to-end)

- `./gradlew build` green.
- Run via `TestDonationsApplication`, then with a TREASURER session:
  - `GET /api/v1/reports/balance/timeseries?from=2026-01-01&groupBy=MONTH` → periods ending at the
    current month, `to` echoed as today.
  - `GET /api/v1/reports/balance/timeseries?from=2020-01-01&to=2030-12-31&groupBy=MONTH` → both
    bounds clamped in the echoed range; no future periods.
  - `GET /api/v1/reports/balance/timeseries?from=2030-01-01&groupBy=MONTH` → 400 ProblemDetail.
- Confirm `/v3/api-docs` (dev profile) lists the endpoint and the field descriptions.
- Release: a `feat:` commit cuts a minor version, and `.github/workflows/release.yml:135`
  regenerates and publishes `@jorgetroya80/donations-api-client` with the new types. No manual
  client step.

---

## Frontend rendering contract

The frontend has no written specification of its own, so this section is the contract for the
chart. Three payload behaviors will produce a **false alarm** if rendered naively.

**Plot `coverageRatio`, not the two money lines.** One series, y-axis starting at 0, with a
reference line at **1,0** labelled as the break-even point. Above the line, income covers expenses.
Format as a percentage (`2.0769` → `208 %`) or as a ratio (`2,08`) — the API deliberately returns
the raw number so the locale and precision shown are the frontend's choice.

**`coverageRatio: null` is a gap, never zero.** `null` means the month had no expenses, so coverage
is undefined. Most charting libraries coerce `null` to `0` — and `0` on this chart reads as "income
covered nothing", the exact opposite of the truth. Break the line, or skip the point.

    { "periodStart": "2026-07-01", "periodEnd": "2026-07-31",
      "totalIncome": 8900.00, "totalExpenses": 0.00,
      "netBalance": 8900.00, "coverageRatio": null }
    → line breaks between June and August; no marker drawn for July.

Distinguish this from the genuine alarm, which is a real number and must be plotted:

    { "periodStart": "2026-07-01", "periodEnd": "2026-07-31",
      "totalIncome": 0.00, "totalExpenses": 3900.00,
      "netBalance": -3900.00, "coverageRatio": 0.0000 }
    → point at 0, well below the 1,0 line.

**The last period is usually incomplete — dim it.** A bucket is partial when `periodEnd` is earlier
than the last day of its own month (or `periodStart` later than the first day, for the very first
bucket). Monthly expenses such as rent land on day one while offerings accumulate weekly, so an
in-progress month shows artificially low coverage that means nothing.

    { "periodStart": "2026-08-01", "periodEnd": "2026-08-26",
      "totalIncome": 6200.00, "totalExpenses": 4050.00,
      "netBalance": 2150.00, "coverageRatio": 1.5309 }
    → periodEnd (26 Aug) < 31 Aug ⇒ partial. Render dimmed/dashed, label "en curso".

**Read the range from the response, not from the request.** `from` and `to` come back clamped, so a
request for `to=2026-12-31` returns `to=2026-08-26`. Axis labels and any "período" caption should
use the echoed values.

**`periods: []` is a valid empty state**, not an error — show "sin datos", not a failure message.
