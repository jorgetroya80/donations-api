package com.example.donations.report

import com.example.donations.donation.DonationRepository
import com.example.donations.donation.DonationType
import com.example.donations.donor.DonorRepository
import com.example.donations.expense.ExpenseCategory
import com.example.donations.expense.ExpenseRepository
import com.example.donations.infrastructure.defaultYearRange
import com.example.donations.infrastructure.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

@Service
@Transactional(readOnly = true)
class ReportService(
    private val donationRepository: DonationRepository,
    private val expenseRepository: ExpenseRepository,
    private val donorRepository: DonorRepository,
    private val clock: Clock,
) {

    fun donationSummary(from: LocalDate?, to: LocalDate?): DonationSummaryResponse {
        val (effectiveFrom, effectiveTo) = defaultYearRange(from, to, clock)

        val rows = donationRepository.sumByTypeAndDateBetween(effectiveFrom, effectiveTo)
        val totalsByType = rows.map { row ->
            DonationSummaryResponse.TypeTotal(
                type = row[0] as DonationType,
                total = row[1] as BigDecimal,
            )
        }
        val grandTotal = donationRepository.sumAmountByDateBetween(effectiveFrom, effectiveTo) ?: BigDecimal.ZERO

        return DonationSummaryResponse(
            from = effectiveFrom,
            to = effectiveTo,
            totalsByType = totalsByType,
            grandTotal = grandTotal,
        )
    }

    fun expenseSummary(from: LocalDate?, to: LocalDate?): ExpenseSummaryResponse {
        val (effectiveFrom, effectiveTo) = defaultYearRange(from, to, clock)

        val rows = expenseRepository.sumByCategoryAndDateBetween(effectiveFrom, effectiveTo)
        val totalsByCategory = rows.map { row ->
            ExpenseSummaryResponse.CategoryTotal(
                category = row[0] as ExpenseCategory,
                total = row[1] as BigDecimal,
            )
        }
        val grandTotal = expenseRepository.sumAmountByDateBetween(effectiveFrom, effectiveTo) ?: BigDecimal.ZERO

        return ExpenseSummaryResponse(
            from = effectiveFrom,
            to = effectiveTo,
            totalsByCategory = totalsByCategory,
            grandTotal = grandTotal,
        )
    }

    fun balance(from: LocalDate?, to: LocalDate?): BalanceResponse {
        val (effectiveFrom, effectiveTo) = defaultYearRange(from, to, clock)

        val totalIncome = donationRepository.sumAmountByDateBetween(effectiveFrom, effectiveTo) ?: BigDecimal.ZERO
        val totalExpenses = expenseRepository.sumAmountByDateBetween(effectiveFrom, effectiveTo) ?: BigDecimal.ZERO

        return BalanceResponse(
            from = effectiveFrom,
            to = effectiveTo,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netBalance = totalIncome.subtract(totalExpenses),
        )
    }

    fun balanceTimeseries(from: LocalDate, to: LocalDate?, groupBy: GroupBy): BalanceTimeseriesResponse {
        // The earlier of the two tables' first records, or null when the ledger is empty.
        // One populated table with the other empty is an ordinary range, not an empty ledger.
        val earliest = listOfNotNull(donationRepository.minDonationDate(), expenseRepository.minExpenseDate())
            .minOrNull()

        val bounds = resolveTimeseriesBounds(from, to, LocalDate.now(clock), earliest)

        return BalanceTimeseriesResponse(
            from = bounds.from,
            to = bounds.to,
            groupBy = groupBy,
            periods = if (!bounds.holdsRecords) {
                emptyList()
            } else {
                buildBalancePeriods(
                    from = bounds.from,
                    to = bounds.to,
                    incomeByMonth = monthlyTotals(donationRepository.sumByMonthAndDateBetween(bounds.from, bounds.to)),
                    expensesByMonth = monthlyTotals(expenseRepository.sumByMonthAndDateBetween(bounds.from, bounds.to)),
                )
            },
        )
    }

    fun donorStatement(donorId: Long, from: LocalDate?, to: LocalDate?): DonorStatementResponse {
        val donor = donorRepository.findById(donorId)
            .orElseThrow { NotFoundException("Donor not found with id: $donorId") }

        val (effectiveFrom, effectiveTo) = defaultYearRange(from, to, clock)

        val donations = donationRepository.findByDonorIdAndDonationDateBetween(donorId, effectiveFrom, effectiveTo)
        val entries = donations.map { d ->
            DonorStatementResponse.DonationEntry(
                id = d.id!!,
                amount = d.amount,
                date = d.donationDate,
                type = d.donationType,
                paymentMethod = d.paymentMethod,
            )
        }
        val total = entries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }

        return DonorStatementResponse(
            donorId = donorId,
            donorName = donor.fullName,
            from = effectiveFrom,
            to = effectiveTo,
            donations = entries,
            total = total,
        )
    }

    // year() / month() come back as some Number subtype depending on dialect and
    // driver, so they are read as Number rather than cast to a concrete type.
    private fun monthlyTotals(rows: List<Array<Any>>): Map<YearMonth, BigDecimal> =
        rows.associate { row ->
            YearMonth.of((row[0] as Number).toInt(), (row[1] as Number).toInt()) to row[2] as BigDecimal
        }
}
