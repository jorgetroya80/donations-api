package com.example.donations.expense

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/expenses")
@PreAuthorize("hasAnyRole('OPERATOR', 'TREASURER')")
class ExpenseController(
    private val expenseService: ExpenseService,
) {

    @GetMapping
    fun listExpenses(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        pageable: Pageable,
    ): Page<ExpenseResponse> {
        return expenseService.listExpenses(pageable, from, to)
            .map { ExpenseResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getExpense(@PathVariable id: Long): ExpenseResponse {
        return ExpenseResponse.from(expenseService.getExpense(id))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExpense(
        @Valid @RequestBody request: CreateExpenseRequest,
    ): ExpenseResponse {
        return ExpenseResponse.from(expenseService.createExpense(request))
    }

    @PutMapping("/{id}")
    fun updateExpense(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateExpenseRequest,
    ): ExpenseResponse {
        return ExpenseResponse.from(expenseService.updateExpense(id, request))
    }
}
