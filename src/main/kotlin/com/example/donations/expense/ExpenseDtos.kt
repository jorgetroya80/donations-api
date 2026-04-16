package com.example.donations.expense

import com.example.donations.donation.PaymentMethod
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class CreateExpenseRequest(

    @field:NotNull(message = "Amount is required")
    @field:DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    val amount: BigDecimal?,

    @field:NotNull(message = "Expense date is required")
    @field:PastOrPresent(message = "Expense date cannot be in the future")
    val expenseDate: LocalDate?,

    @field:NotNull(message = "Category is required")
    val category: ExpenseCategory?,

    @field:NotBlank(message = "Description is required")
    val description: String?,

    val vendor: String? = null,

    @field:NotNull(message = "Payment method is required")
    val paymentMethod: PaymentMethod?,
)

data class UpdateExpenseRequest(
    val amount: BigDecimal? = null,
    val expenseDate: LocalDate? = null,
    val category: ExpenseCategory? = null,
    val description: String? = null,
    val vendor: String? = null,
    val paymentMethod: PaymentMethod? = null,
)

data class ExpenseResponse(
    val id: Long,
    val amount: BigDecimal,
    val expenseDate: LocalDate,
    val category: ExpenseCategory,
    val description: String,
    val vendor: String?,
    val paymentMethod: PaymentMethod,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    companion object {
        fun from(expense: Expense): ExpenseResponse = ExpenseResponse(
            id = expense.id!!,
            amount = expense.amount,
            expenseDate = expense.expenseDate,
            category = expense.category,
            description = expense.description,
            vendor = expense.vendor,
            paymentMethod = expense.paymentMethod,
            createdAt = expense.createdAt,
            updatedAt = expense.updatedAt,
        )
    }
}
