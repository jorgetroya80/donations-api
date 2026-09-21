package com.example.donations.report

import com.example.donations.donation.DonationType
import com.example.donations.donation.PaymentMethod
import com.example.donations.expense.ExpenseCategory
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

data class DonationSummaryResponse(
    val from: LocalDate,
    val to: LocalDate,
    val totalsByType: List<TypeTotal>,
    val grandTotal: BigDecimal,
) {
    data class TypeTotal(
        val type: DonationType,
        val total: BigDecimal,
    )
}

data class ExpenseSummaryResponse(
    val from: LocalDate,
    val to: LocalDate,
    val totalsByCategory: List<CategoryTotal>,
    val grandTotal: BigDecimal,
) {
    data class CategoryTotal(
        val category: ExpenseCategory,
        val total: BigDecimal,
    )
}

data class BalanceResponse(
    val from: LocalDate,
    val to: LocalDate,
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val netBalance: BigDecimal,
)

enum class GroupBy {
    MONTH,
}

data class BalanceTimeseriesResponse(
    val from: LocalDate,
    val to: LocalDate,
    val groupBy: GroupBy,
    val periods: List<PeriodBalance>,
) {
    data class PeriodBalance(
        @field:Schema(
            description = "Start of the measured span, clipped to the queried range. " +
                "Later than the first day of the month for the first period.",
        )
        val periodStart: LocalDate,
        @field:Schema(
            description = "End of the measured span, clipped to the queried range. " +
                "When earlier than the last day of the month, the period is still in progress " +
                "and its totals are incomplete.",
        )
        val periodEnd: LocalDate,
        val totalIncome: BigDecimal,
        val totalExpenses: BigDecimal,
        val netBalance: BigDecimal,
        // The global inclusion policy is non_null, which would drop this field entirely
        // for a period with no expenses. An explicit null is the documented contract:
        // it tells the consumer coverage is undefined rather than leaving it to infer
        // that from a missing key.
        @field:JsonInclude(JsonInclude.Include.ALWAYS)
        @field:Schema(
            description = "Income divided by expenses, scale 4. Null when the period had no " +
                "expenses, meaning coverage is undefined: render it as a gap, never as zero.",
            nullable = true,
        )
        val coverageRatio: BigDecimal?,
    )
}

data class DonorStatementResponse(
    val donorId: Long,
    val donorName: String,
    val from: LocalDate,
    val to: LocalDate,
    val donations: List<DonationEntry>,
    val total: BigDecimal,
) {
    data class DonationEntry(
        val id: Long,
        val amount: BigDecimal,
        val date: LocalDate,
        val type: DonationType,
        val paymentMethod: PaymentMethod,
    )
}
