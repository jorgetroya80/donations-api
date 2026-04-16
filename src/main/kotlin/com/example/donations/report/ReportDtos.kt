package com.example.donations.report

import com.example.donations.donation.DonationType
import com.example.donations.donation.PaymentMethod
import com.example.donations.expense.ExpenseCategory
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
