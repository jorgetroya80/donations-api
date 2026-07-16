package com.example.donations.infrastructure

import java.time.LocalDate
import java.time.Year

/** Resolves optional query-param bounds to an inclusive range, defaulting to the current year. */
fun defaultYearRange(from: LocalDate?, to: LocalDate?): Pair<LocalDate, LocalDate> {
    val year = Year.now().value
    return (from ?: LocalDate.of(year, 1, 1)) to (to ?: LocalDate.of(year, 12, 31))
}
