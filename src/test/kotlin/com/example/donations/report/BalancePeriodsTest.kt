package com.example.donations.report

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("Balance Periods Tests")
class BalancePeriodsTest {

    private fun money(value: String) = BigDecimal(value)

    private fun assertAmount(expected: String, actual: BigDecimal, label: String) {
        assertEquals(
            0,
            BigDecimal(expected).compareTo(actual),
            "$label: expected $expected but was $actual",
        )
    }

    @Test
    @DisplayName("Consecutive months are returned in order, one period per calendar month")
    fun consecutiveMonthsInOrder() {
        val periods = buildBalancePeriods(
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 3, 31),
            incomeByMonth = mapOf(
                YearMonth.of(2026, 1) to money("100.00"),
                YearMonth.of(2026, 2) to money("200.00"),
                YearMonth.of(2026, 3) to money("300.00"),
            ),
            expensesByMonth = mapOf(
                YearMonth.of(2026, 1) to money("50.00"),
                YearMonth.of(2026, 2) to money("50.00"),
                YearMonth.of(2026, 3) to money("50.00"),
            ),
        )

        assertEquals(3, periods.size)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)),
            periods.map { it.periodStart },
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
            periods.map { it.periodEnd },
        )
        assertAmount("100.00", periods[0].totalIncome, "january income")
        assertAmount("250.00", periods[2].netBalance, "march net balance")
    }

    @Test
    @DisplayName("A month present in neither map is zero-filled with a null coverage ratio")
    fun gapMonthIsZeroFilled() {
        val periods = buildBalancePeriods(
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 3, 31),
            incomeByMonth = mapOf(
                YearMonth.of(2026, 1) to money("100.00"),
                YearMonth.of(2026, 3) to money("300.00"),
            ),
            expensesByMonth = mapOf(
                YearMonth.of(2026, 1) to money("50.00"),
                YearMonth.of(2026, 3) to money("150.00"),
            ),
        )

        assertEquals(3, periods.size)
        val february = periods[1]
        assertEquals(LocalDate.of(2026, 2, 1), february.periodStart)
        assertEquals(LocalDate.of(2026, 2, 28), february.periodEnd)
        assertAmount("0", february.totalIncome, "february income")
        assertAmount("0", february.totalExpenses, "february expenses")
        assertAmount("0", february.netBalance, "february net balance")
        assertNull(february.coverageRatio)
    }

    @Test
    @DisplayName("First and last buckets are clipped to the range; interior buckets span whole months")
    fun edgeBucketsAreClipped() {
        val periods = buildBalancePeriods(
            from = LocalDate.of(2026, 1, 17),
            to = LocalDate.of(2026, 3, 9),
            incomeByMonth = emptyMap(),
            expensesByMonth = emptyMap(),
        )

        assertEquals(3, periods.size)
        assertEquals(LocalDate.of(2026, 1, 17), periods[0].periodStart)
        assertEquals(LocalDate.of(2026, 1, 31), periods[0].periodEnd)
        assertEquals(LocalDate.of(2026, 2, 1), periods[1].periodStart)
        assertEquals(LocalDate.of(2026, 2, 28), periods[1].periodEnd)
        assertEquals(LocalDate.of(2026, 3, 1), periods[2].periodStart)
        assertEquals(LocalDate.of(2026, 3, 9), periods[2].periodEnd)
    }

    @Test
    @DisplayName("Coverage ratio is scale 4 HALF_UP on a non-terminating division")
    fun coverageRatioIsScaleFourHalfUp() {
        val periods = buildBalancePeriods(
            from = LocalDate.of(2026, 5, 1),
            to = LocalDate.of(2026, 5, 31),
            incomeByMonth = mapOf(YearMonth.of(2026, 5) to money("8100.00")),
            expensesByMonth = mapOf(YearMonth.of(2026, 5) to money("3900.00")),
        )

        val ratio = assertNotNull(periods.single().coverageRatio)
        assertEquals("2.0769", ratio.toPlainString())
    }

    @Test
    @DisplayName("Zero expenses with income yields a null coverage ratio")
    fun zeroExpensesYieldsNullRatio() {
        val periods = buildBalancePeriods(
            from = LocalDate.of(2026, 5, 1),
            to = LocalDate.of(2026, 5, 31),
            incomeByMonth = mapOf(YearMonth.of(2026, 5) to money("4100.00")),
            expensesByMonth = mapOf(YearMonth.of(2026, 5) to money("0.00")),
        )

        val period = periods.single()
        assertAmount("4100.00", period.netBalance, "net balance")
        assertNull(period.coverageRatio)
    }

    @Test
    @DisplayName("Zero income with non-zero expenses yields a coverage ratio of 0.0000")
    fun zeroIncomeYieldsZeroRatio() {
        val periods = buildBalancePeriods(
            from = LocalDate.of(2026, 5, 1),
            to = LocalDate.of(2026, 5, 31),
            incomeByMonth = mapOf(YearMonth.of(2026, 5) to money("0.00")),
            expensesByMonth = mapOf(YearMonth.of(2026, 5) to money("1950.00")),
        )

        val ratio = assertNotNull(periods.single().coverageRatio)
        assertEquals("0.0000", ratio.toPlainString())
    }

    @Test
    @DisplayName("A range inside a single month yields exactly one clipped period")
    fun singleMonthRangeYieldsOnePeriod() {
        val periods = buildBalancePeriods(
            from = LocalDate.of(2026, 5, 12),
            to = LocalDate.of(2026, 5, 12),
            incomeByMonth = mapOf(YearMonth.of(2026, 5) to money("75.00")),
            expensesByMonth = mapOf(YearMonth.of(2026, 5) to money("25.00")),
        )

        val period = periods.single()
        assertEquals(LocalDate.of(2026, 5, 12), period.periodStart)
        assertEquals(LocalDate.of(2026, 5, 12), period.periodEnd)
        assertAmount("50.00", period.netBalance, "net balance")
    }

    // --- Bounds resolution ---

    private val earliestRecord = LocalDate.of(2026, 1, 15)

    @Test
    @DisplayName("Omitted to resolves to today")
    fun omittedToResolvesToToday() {
        val (from, to) = resolveTimeseriesBounds(
            from = LocalDate.of(2026, 2, 1),
            to = null,
            today = LocalDate.of(2026, 8, 26),
            earliest = earliestRecord,
        )

        assertEquals(LocalDate.of(2026, 2, 1), from)
        assertEquals(LocalDate.of(2026, 8, 26), to)
    }

    @Test
    @DisplayName("A to in the future clamps back to today")
    fun futureToClampsToToday() {
        val (_, to) = resolveTimeseriesBounds(
            from = LocalDate.of(2026, 2, 1),
            to = LocalDate.of(2026, 12, 31),
            today = LocalDate.of(2026, 8, 26),
            earliest = earliestRecord,
        )

        assertEquals(LocalDate.of(2026, 8, 26), to)
    }

    @Test
    @DisplayName("A to in the past is left alone")
    fun pastToIsUnchanged() {
        val (_, to) = resolveTimeseriesBounds(
            from = LocalDate.of(2026, 2, 1),
            to = LocalDate.of(2026, 3, 31),
            today = LocalDate.of(2026, 8, 26),
            earliest = earliestRecord,
        )

        assertEquals(LocalDate.of(2026, 3, 31), to)
    }

    @Test
    @DisplayName("A from before the earliest record clamps forward to it")
    fun fromBeforeEarliestClampsForward() {
        val (from, _) = resolveTimeseriesBounds(
            from = LocalDate.of(2020, 1, 1),
            to = null,
            today = LocalDate.of(2026, 8, 26),
            earliest = earliestRecord,
        )

        assertEquals(earliestRecord, from)
    }

    @Test
    @DisplayName("A from after the earliest record is left alone")
    fun fromAfterEarliestIsUnchanged() {
        val (from, _) = resolveTimeseriesBounds(
            from = LocalDate.of(2026, 4, 1),
            to = null,
            today = LocalDate.of(2026, 8, 26),
            earliest = earliestRecord,
        )

        assertEquals(LocalDate.of(2026, 4, 1), from)
    }

    @Test
    @DisplayName("A from after the resolved to is rejected")
    fun fromAfterResolvedToIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            resolveTimeseriesBounds(
                from = LocalDate.of(2027, 6, 1),
                to = null,
                today = LocalDate.of(2026, 8, 26),
                earliest = earliestRecord,
            )
        }
    }

    /**
     * The pair below is the reason the clock is injected. On the last day of the year the range
     * must still end that day rather than roll into the next, and on the first day of the new year
     * a January bucket must exist immediately. A UTC server serving a Europe/Madrid church is one
     * to two hours behind local midnight, so between 00:00 and 02:00 local it crosses this
     * boundary at the wrong moment and hides the day's records.
     */
    @Test
    @DisplayName("On December 31 the range ends that day and the last bucket is December")
    fun yearEndRangeEndsOnDecember31() {
        val (from, to) = resolveTimeseriesBounds(
            from = LocalDate.of(2026, 1, 1),
            to = null,
            today = LocalDate.of(2026, 12, 31),
            earliest = LocalDate.of(2026, 1, 1),
        )

        assertEquals(LocalDate.of(2026, 1, 1), from)
        assertEquals(LocalDate.of(2026, 12, 31), to)

        val periods = buildBalancePeriods(from, to, emptyMap(), emptyMap())
        assertEquals(12, periods.size)
        assertEquals(LocalDate.of(2026, 12, 31), periods.last().periodEnd)
    }

    @Test
    @DisplayName("On January 1 the range extends into the new year and a January bucket exists")
    fun newYearAddsJanuaryBucket() {
        val (from, to) = resolveTimeseriesBounds(
            from = LocalDate.of(2026, 1, 1),
            to = null,
            today = LocalDate.of(2027, 1, 1),
            earliest = LocalDate.of(2026, 1, 1),
        )

        assertEquals(LocalDate.of(2027, 1, 1), to)

        val periods = buildBalancePeriods(from, to, emptyMap(), emptyMap())
        assertEquals(13, periods.size)
        val january = periods.last()
        assertEquals(LocalDate.of(2027, 1, 1), january.periodStart)
        assertEquals(LocalDate.of(2027, 1, 1), january.periodEnd)
    }

    @Test
    @DisplayName("Empty maps over a range still yield zero-filled periods")
    fun emptyMapsYieldZeroFilledPeriods() {
        val periods = buildBalancePeriods(
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 4, 30),
            incomeByMonth = emptyMap(),
            expensesByMonth = emptyMap(),
        )

        assertEquals(4, periods.size)
        periods.forEach { period ->
            assertAmount("0", period.totalIncome, "income")
            assertAmount("0", period.totalExpenses, "expenses")
            assertAmount("0", period.netBalance, "net balance")
            assertNull(period.coverageRatio)
        }
    }
}
