package com.example.donations

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pins "today" for integration tests that seed fixed calendar dates.
 *
 * Without it, tests that omit `from`/`to` fall through `defaultYearRange` to the real current
 * year while their seed data stays in 2026 — so on 2027-01-01 the seeded rows fall outside the
 * default window and the asserted totals silently become zero.
 *
 * The zone matches the application default rather than the JVM's, so the fixture cannot pass on
 * a Madrid laptop and fail in a UTC CI container.
 */
@TestConfiguration(proxyBeanMethods = false)
class FixedClockConfiguration {

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Madrid")

        /** The date every test using this fixture sees as today. */
        val TODAY: LocalDate = LocalDate.of(2026, 8, 26)
    }

    // A distinct bean name, plus @Primary: same-name beans would be a definition clash with
    // TimeConfig's, which Boot rejects rather than overriding.
    @Bean
    @Primary
    fun fixedClock(): Clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE)
}
