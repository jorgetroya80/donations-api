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
 * Clamps the requested range to what can actually be reported: [to] never runs past [today], and
 * [from] never runs before [earliest], the first recorded transaction. Takes [today] as a value
 * rather than reading a clock, so the year-end boundary is testable without waiting for it.
 *
 * @throws IllegalArgumentException if the clamped range is inverted, which the error handler
 *   renders as a 400.
 */
internal fun resolveTimeseriesBounds(
    from: LocalDate,
    to: LocalDate?,
    today: LocalDate,
    earliest: LocalDate,
): Pair<LocalDate, LocalDate> {
    val resolvedTo = minOf(to ?: today, today)
    val resolvedFrom = maxOf(from, earliest)
    require(resolvedFrom <= resolvedTo) { "from ($resolvedFrom) must not be after to ($resolvedTo)" }
    return resolvedFrom to resolvedTo
}

private fun coverageRatio(income: BigDecimal, expenses: BigDecimal): BigDecimal? =
    if (expenses.signum() == 0) null
    else income.divide(expenses, 4, RoundingMode.HALF_UP)
