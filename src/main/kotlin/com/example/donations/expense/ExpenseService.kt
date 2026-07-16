package com.example.donations.expense

import com.example.donations.infrastructure.defaultYearRange
import com.example.donations.infrastructure.error.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ExpenseService(
    private val expenseRepository: ExpenseRepository,
) {

    fun listExpenses(pageable: Pageable, from: LocalDate?, to: LocalDate?): Page<Expense> {
        val (effectiveFrom, effectiveTo) = defaultYearRange(from, to)
        return expenseRepository.findByExpenseDateBetween(effectiveFrom, effectiveTo, pageable)
    }

    fun getExpense(id: Long): Expense {
        return expenseRepository.findById(id)
            .orElseThrow { NotFoundException("Expense not found with id: $id") }
    }

    @Transactional
    fun createExpense(request: CreateExpenseRequest): Expense {
        val expense = Expense(
            amount = request.amount!!,
            expenseDate = request.expenseDate!!,
            category = request.category!!,
            description = request.description!!,
            vendor = request.vendor,
            paymentMethod = request.paymentMethod!!,
        )
        return expenseRepository.save(expense)
    }

    @Transactional
    fun updateExpense(id: Long, request: UpdateExpenseRequest): Expense {
        val expense = expenseRepository.findById(id)
            .orElseThrow { NotFoundException("Expense not found with id: $id") }

        request.amount?.let { expense.amount = it }
        request.expenseDate?.let { expense.expenseDate = it }
        request.category?.let { expense.category = it }
        request.description?.let { expense.description = it }
        request.vendor?.let { expense.vendor = it }
        request.paymentMethod?.let { expense.paymentMethod = it }

        return expenseRepository.save(expense)
    }
}
