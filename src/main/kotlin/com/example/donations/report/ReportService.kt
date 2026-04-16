package com.example.donations.report

import com.example.donations.donation.DonationRepository
import com.example.donations.donation.DonationType
import com.example.donations.donor.DonorRepository
import com.example.donations.expense.ExpenseCategory
import com.example.donations.expense.ExpenseRepository
import com.example.donations.infrastructure.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Year

@Service
@Transactional(readOnly = true)
class ReportService(
    private val donationRepository: DonationRepository,
    private val expenseRepository: ExpenseRepository,
    private val donorRepository: DonorRepository,
) {

    fun donationSummary(from: LocalDate?, to: LocalDate?): DonationSummaryResponse {
        val effectiveFrom = from ?: LocalDate.of(Year.now().value, 1, 1)
        val effectiveTo = to ?: LocalDate.of(Year.now().value, 12, 31)

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
        val effectiveFrom = from ?: LocalDate.of(Year.now().value, 1, 1)
        val effectiveTo = to ?: LocalDate.of(Year.now().value, 12, 31)

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
        val effectiveFrom = from ?: LocalDate.of(Year.now().value, 1, 1)
        val effectiveTo = to ?: LocalDate.of(Year.now().value, 12, 31)

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

    fun donorStatement(donorId: Long, from: LocalDate?, to: LocalDate?): DonorStatementResponse {
        val donor = donorRepository.findById(donorId)
            .orElseThrow { NotFoundException("Donor not found with id: $donorId") }

        val effectiveFrom = from ?: LocalDate.of(Year.now().value, 1, 1)
        val effectiveTo = to ?: LocalDate.of(Year.now().value, 12, 31)

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
}
