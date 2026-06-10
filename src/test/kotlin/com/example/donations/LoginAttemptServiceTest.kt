package com.example.donations

import com.example.donations.user.LoginAttemptService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("Login Attempt Service Tests")
class LoginAttemptServiceTest {

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) {
            now += duration
        }
    }

    private val clock = MutableClock(Instant.parse("2026-06-10T10:00:00Z"))
    private val service = LoginAttemptService(clock)

    @Test
    @DisplayName("Account is not locked below the failure threshold")
    fun notLockedBelowThreshold() {
        repeat(LoginAttemptService.MAX_FAILURES - 1) { service.recordFailure("admin") }
        assertFalse(service.isLocked("admin"))
    }

    @Test
    @DisplayName("Account locks at the failure threshold")
    fun locksAtThreshold() {
        repeat(LoginAttemptService.MAX_FAILURES) { service.recordFailure("admin") }
        assertTrue(service.isLocked("admin"))
    }

    @Test
    @DisplayName("Successful login resets the counter")
    fun successResetsCounter() {
        repeat(LoginAttemptService.MAX_FAILURES - 1) { service.recordFailure("admin") }
        service.recordSuccess("admin")
        repeat(LoginAttemptService.MAX_FAILURES - 1) { service.recordFailure("admin") }
        assertFalse(service.isLocked("admin"))
    }

    @Test
    @DisplayName("Lock expires after the lock duration")
    fun lockExpires() {
        repeat(LoginAttemptService.MAX_FAILURES) { service.recordFailure("admin") }
        assertTrue(service.isLocked("admin"))

        clock.advance(LoginAttemptService.LOCK_DURATION.plusSeconds(1))
        assertFalse(service.isLocked("admin"))
    }

    @Test
    @DisplayName("Username matching is case-insensitive")
    fun usernameCaseInsensitive() {
        repeat(LoginAttemptService.MAX_FAILURES) { service.recordFailure("Admin") }
        assertTrue(service.isLocked("admin"))
    }
}
