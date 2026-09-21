# ADR-006: Timeseries response envelope for report endpoints

## Status

Proposed

## Date

2026-08-26

## Context

Every reporting endpoint today answers a question about a **single date range**:
`GET /api/v1/reports/donations`, `/expenses` and `/balance` all take optional `from`/`to`, resolve
them through `defaultYearRange` (`src/main/kotlin/com/example/donations/infrastructure/DateRanges.kt:7`)
and return one set of totals for that whole window
(`src/main/kotlin/com/example/donations/report/ReportService.kt:70-84`).

The donations-frontend dashboard needs a different class of question: **how a figure moves month
over month**. Its first chart plots income against expenses per month; the treasurer's actual
question behind that chart is "does income still cover expenses, and is the cushion shrinking?".
A second chart (donations by type per month) asks the same shape of question about a different
measure, and further report charts are likely.

There is currently no month bucketing anywhere in the codebase, so the frontend can only produce a
monthly series by calling a range endpoint once per month — twelve to twenty-four round trips per
dashboard load — or by downloading raw rows through a paged list endpoint and bucketing them
client-side, which breaks as the tables grow.

Three forces shape any answer:

1. **This is the first of several timeseries endpoints, not a one-off.** Whatever envelope the
   first one adopts is what the second and third will copy, and by the third it is expensive to
   change. The response shape is therefore a convention decision, not a local one.
2. **Range edges are ambiguous and the ambiguity is invisible.** A caller asking for a full
   calendar year in August is asking partly about the future; a caller asking from before the
   church kept records is asking about months that never existed; the current month is always
   incomplete. Every one of these produces a bucket that *looks* like a normal data point on a
   chart while meaning something entirely different.
3. **The frontend has no written specification.** The dashboard charts are being built now, and
   the API contract is the only shared document. Semantics that live only in a developer's head
   at design time will not survive the handoff.

[`docs/balance-timeseries.md`](../balance-timeseries.md) drives this decision, specified in
[`docs/balance-timeseries-spec.md`](../balance-timeseries-spec.md) and sequenced in
[`plans/balance-timeseries.md`](../../plans/balance-timeseries.md). The first implementation is
`GET /api/v1/reports/balance/timeseries`.

## Decision

All report timeseries endpoints share one **envelope**, one **bounds-resolution rule**, and one
**bucketing rule**. Only the contents of each period item vary per endpoint.

**Envelope.** The response carries the resolved range, the granularity, and an ordered array of
period items:

```json
{
  "from": "2025-03-17",
  "to": "2026-08-26",
  "groupBy": "MONTH",
  "periods": [
    { "periodStart": "2025-03-17", "periodEnd": "2025-03-31", "…": "endpoint-specific fields" }
  ]
}
```

Endpoints are addressed as a `/timeseries` sub-resource of the range endpoint they extend —
`/api/v1/reports/balance/timeseries` is the per-period form of `/api/v1/reports/balance`, and
reuses that response's field names (`totalIncome`, `totalExpenses`, `netBalance`,
`src/main/kotlin/com/example/donations/report/ReportDtos.kt:33-39`).

**Bounds resolution.** `from` is **required**; `to` is optional. Both are then clamped to the
range for which data can exist:

- `to` is clamped backwards to today when absent or in the future — there is no future to report on.
  "Today" is resolved through an injected `Clock` zoned to the church (`app.timezone`, default
  `Europe/Madrid`), not the JVM default: the deploy target runs UTC, so for the first one or two
  hours of each local day an unzoned clock reports yesterday and silently clamps out records
  dated today.
- `from` is clamped forwards to the earliest recorded transaction — there is no history before the
  first record.
- The response echoes the **clamped** values, not the requested ones.
- Validation is against the caller's **own** `from`, not the clamped one: `from > to` is a 400,
  which also covers a `from` in the future. Clamping must never manufacture an error — asking
  about a period that predates the first record is well formed, and rejecting it would quote
  dates the caller never sent.
- With no records in the window — an empty ledger, or a window ending before the first record —
  `periods` is `[]` and the echoed range is the requested `from` with the resolved `to`. Both are
  valid states, not errors, and the envelope is never inverted.

**Bucketing.** Periods are calendar buckets of the `groupBy` unit, **clipped** to the resolved
range rather than snapped outward to whole units. The first and last period may therefore be
partial, and `periodStart`/`periodEnd` always state exactly the span that was measured. Periods
with no underlying rows are **zero-filled**, not omitted: a month with no income and real expenses
is precisely the signal these charts exist to surface, and omitting it would also break even
spacing on a chart's x-axis.

**Granularity.** `groupBy` is a required enum whose only legal value today is `MONTH`. `WEEK` and
`DAY` are deliberately not shipped (see Consequences).

**Derived values, not verdicts.** Period items may carry values derived from the aggregates when
the derivation is money arithmetic the API already owns — `netBalance`, or `coverageRatio` on the
balance series. They do **not** carry interpretation: no "is this healthy" flag, no trend
classification, no threshold evaluation. Where a derived value is undefined it is `null`, never a
sentinel.

## Alternatives Considered

### Frontend loops an existing range endpoint per month
- Pros: zero backend work; ships immediately.
- Cons: 12–24 round trips per dashboard load; every client re-implements bucketing, zero-fill and
  edge clamping; the rules above end up duplicated and divergent across clients.
- Rejected: the correctness rules are the hard part of this feature, and they belong in one place.

### Frontend fetches raw rows and buckets client-side
- Pros: no new endpoint; maximum client flexibility.
- Cons: donation and expense list endpoints are paged; the payload grows without bound; the client
  must reproduce the money arithmetic.
- Rejected: does not survive data growth.

### One generic `/reports/timeseries` with a `metrics` parameter
- Pros: a single endpoint serves every chart.
- Cons: polymorphic response typed as a union in the generated client; the endpoint has to explain
  which fields accompany which metric; parameter combinations multiply.
- Rejected: configurability nobody asked for, and it makes the generated TypeScript worse for
  every consumer.

### Snapping edge buckets outward to whole calendar units
- Pros: every bucket is a full month, so ratios are directly comparable across the whole series.
- Cons: the final bucket claims a `periodEnd` in the future while holding partial data — the same
  distortion as clipping, but concealed rather than stated.
- Rejected: a chart cannot dim what it cannot detect. Clipping makes incompleteness visible by
  comparing `periodEnd` against the unit's true end.

### Dropping partial edge buckets entirely
- Pros: every point on the chart is strictly comparable.
- Cons: the current month never appears until it ends — the treasurer loses the view of the month
  they are actually living in.
- Rejected: the in-progress month is the one most worth watching.

### Returning a `covered` verdict flag per period
- Pros: the "income covers expenses" rule lives server-side and cannot drift between clients.
- Cons: it is an interpretation, and the threshold it encodes (exactly 1.0) is a product decision
  that belongs where the chart is designed; a second client wanting a 1.1 cushion gets nothing
  from it.
- Rejected: the API supplies the numbers; the consumer draws the line. Note this is a genuine
  trade-off against the argument for computing `coverageRatio` server-side — the distinction drawn
  here is *arithmetic on money* (API) versus *thresholds on meaning* (consumer).

### Rejecting future dates with a 400
- Pros: consistent with the write side, where `@PastOrPresent` guards
  `src/main/kotlin/com/example/donations/donation/DonationDtos.kt:17`.
- Cons: "show me this calendar year" becomes an error; every client must compute today's date to
  build a legal request.
- Rejected: a future date on a *write* is a typo; on a *read* it is an ordinary year-to-date
  question. Clamping answers it.

## Consequences

**Easier.** A second timeseries endpoint — donations by type per month, the dashboard's other
chart — is a new period-item DTO and two queries; the envelope, the clamping and the zero-fill
rules come for free and are already documented here. Clients that learn one timeseries endpoint
have learned all of them.

**Harder / accepted downsides.**

- **Partial edge buckets are genuinely misleading in isolation.** Monthly expenses such as rent
  land on day one while offerings accumulate weekly, so on the third of the month coverage looks
  catastrophic. The API states the truth (`periodEnd` before the unit's end); presenting it
  honestly is the consumer's job, and for the dashboard that obligation is written down in
  `docs/balance-timeseries.md`.
- **The echoed range may not match the requested one.** A caller asking `to=2026-12-31` gets
  `to=2026-08-26` back. Clients must read the response, not their own request, to know what was
  measured.
- **`null` for undefined derived values requires care at the consumer.** A charting library handed
  `null` may coerce it to zero, and zero on a coverage chart reads as total failure. This is why
  the semantics are carried in `@Schema` descriptions on the DTO fields — so they reach the
  generated client, which is the artifact a frontend developer actually reads — and not only in a
  document in a backend repository.
- **`@Schema` annotations are introduced on one DTO while the other eleven have none.** A
  deliberate inconsistency: this is the only payload whose fields have non-obvious semantics.
- **`WEEK` and `DAY` are unresolved, not merely unbuilt.** Weekly buckets need a boundary rule, and
  the obvious one is wrong here: `date_trunc('week', …)` is ISO Monday-based, which splits each
  Sunday's offering into the week about to close, and weekly coverage swings wildly because
  expenses are monthly while income is weekly. Adding a value to the `groupBy` enum is additive
  and non-breaking, so the decision can wait for a caller that needs it.
- **Status is `Proposed` on purpose.** The envelope has exactly one implementation. It moves to
  `Accepted` when the second timeseries endpoint ships and confirms the shape generalises; until
  then it can be revised in place rather than superseded.
