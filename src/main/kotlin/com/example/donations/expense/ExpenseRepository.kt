package com.example.donations.expense

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ExpenseRepository : JpaRepository<Expense, Long> {

    fun findByExpenseDateBetween(
        from: LocalDate,
        to: LocalDate,
        pageable: Pageable,
    ): Page<Expense>
}
