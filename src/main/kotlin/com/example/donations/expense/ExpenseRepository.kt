package com.example.donations.expense

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.LocalDate

interface ExpenseRepository : JpaRepository<Expense, Long> {

    fun findByExpenseDateBetween(
        from: LocalDate,
        to: LocalDate,
        pageable: Pageable,
    ): Page<Expense>

    @Query(
        "SELECT e.category, SUM(e.amount) FROM Expense e " +
            "WHERE e.expenseDate BETWEEN :from AND :to GROUP BY e.category"
    )
    fun sumByCategoryAndDateBetween(from: LocalDate, to: LocalDate): List<Array<Any>>

    @Query(
        "SELECT SUM(e.amount) FROM Expense e " +
            "WHERE e.expenseDate BETWEEN :from AND :to"
    )
    fun sumAmountByDateBetween(from: LocalDate, to: LocalDate): BigDecimal?

    @Query(
        "SELECT year(e.expenseDate), month(e.expenseDate), SUM(e.amount) FROM Expense e " +
            "WHERE e.expenseDate BETWEEN :from AND :to " +
            "GROUP BY year(e.expenseDate), month(e.expenseDate)"
    )
    fun sumByMonthAndDateBetween(from: LocalDate, to: LocalDate): List<Array<Any>>

    @Query("SELECT MIN(e.expenseDate) FROM Expense e")
    fun minExpenseDate(): LocalDate?
}
