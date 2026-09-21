package com.example.donations.infrastructure

import java.time.Clock
import java.time.LocalDate

/**
 * Resolves optional query-param bounds to an inclusive range, defaulting to the current year.
 *
 * The year comes from [clock], not the JVM default zone: the deploy target runs UTC while the
 * church is Europe/Madrid, so around local midnight — and above all at the New Year rollover —
 * the two disagree about which year "current" means.
 */
fun defaultYearRange(from: LocalDate?, to: LocalDate?, clock: Clock): Pair<LocalDate, LocalDate> {
    val year = LocalDate.now(clock).year
    return (from ?: LocalDate.of(year, 1, 1)) to (to ?: LocalDate.of(year, 12, 31))
}
