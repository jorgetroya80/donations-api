package com.example.donations.report

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Buckets a resolved date range into calendar months, clipping the first and last bucket to
 * [from] / [to] rather than snapping them outward, and zero-filling months absent from both maps.
 * Pure by design: every rule here is unit-tested without a database.
 */
internal fun buildBalancePeriods(
    from: LocalDate,
    to: LocalDate,
    incomeByMonth: Map<YearMonth, BigDecimal>,
    expensesByMonth: Map<YearMonth, BigDecimal>,
): List<BalanceTimeseriesResponse.PeriodBalance> {
    val firstMonth = YearMonth.from(from)
    val lastMonth = YearMonth.from(to)

    return generateSequence(firstMonth) { month -> if (month < lastMonth) month.plusMonths(1) else null }
        .map { month ->
            val income = incomeByMonth[month] ?: BigDecimal.ZERO
            val expenses = expensesByMonth[month] ?: BigDecimal.ZERO
            BalanceTimeseriesResponse.PeriodBalance(
                periodStart = if (month == firstMonth) from else month.atDay(1),
                periodEnd = if (month == lastMonth) to else month.atEndOfMonth(),
                totalIncome = income,
                totalExpenses = expenses,
                netBalance = income.subtract(expenses),
                coverageRatio = coverageRatio(income, expenses),
            )
        }
        .toList()
}

/**
 * The range a timeseries response reports on: [from] and [to] are the bounds to echo, and
 * [holdsRecords] says whether any period can be built from them.
 */
internal data class TimeseriesBounds(
    val from: LocalDate,
    val to: LocalDate,
    val holdsRecords: Boolean,
)

/**
 * Clamps the requested range to what can actually be reported: [to] never runs past [today], and
 * [from] never runs before [earliest], the first recorded transaction, which is null when the
 * ledger is empty. Takes [today] as a value rather than reading a clock, so the year-end boundary
 * is testable without waiting for it.
 *
 * Validation is against the caller's own [from], never the clamped one: asking about a period
 * that predates the church's first record is a well-formed question whose answer is "nothing",
 * and answering it with an inverted-range error would quote dates the caller never sent.
 *
 * @throws IllegalArgumentException if the caller's own range is inverted, which the error handler
 *   renders as a 400.
 */
internal fun resolveTimeseriesBounds(
    from: LocalDate,
    to: LocalDate?,
    today: LocalDate,
    earliest: LocalDate?,
): TimeseriesBounds {
    val resolvedTo = minOf(to ?: today, today)
    require(from <= resolvedTo) { "from ($from) must not be after to ($resolvedTo)" }

    val resolvedFrom = earliest?.let { maxOf(from, it) }
    return if (resolvedFrom == null || resolvedFrom > resolvedTo) {
        // Nothing recorded in the window: echo what was asked for, minus the future.
        TimeseriesBounds(from, resolvedTo, holdsRecords = false)
    } else {
        TimeseriesBounds(resolvedFrom, resolvedTo, holdsRecords = true)
    }
}

private fun coverageRatio(income: BigDecimal, expenses: BigDecimal): BigDecimal? =
    if (expenses.signum() == 0) null
    else income.divide(expenses, 4, RoundingMode.HALF_UP)
