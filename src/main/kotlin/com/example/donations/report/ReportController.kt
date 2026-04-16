package com.example.donations.report

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('TREASURER', 'PASTOR')")
class ReportController(
    private val reportService: ReportService,
) {

    @GetMapping("/donations")
    fun donationSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): DonationSummaryResponse {
        return reportService.donationSummary(from, to)
    }

    @GetMapping("/expenses")
    fun expenseSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): ExpenseSummaryResponse {
        return reportService.expenseSummary(from, to)
    }

    @GetMapping("/balance")
    fun balance(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): BalanceResponse {
        return reportService.balance(from, to)
    }

    @GetMapping("/donors/{id}/statement")
    fun donorStatement(
        @PathVariable id: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): DonorStatementResponse {
        return reportService.donorStatement(id, from, to)
    }
}
