package com.example.donations.user

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory login throttling: [MAX_FAILURES] consecutive failures lock the
 * account for [LOCK_DURATION]. State is per-instance and resets on restart —
 * acceptable for a single-instance deployment.
 */
@Service
class LoginAttemptService(
    private val clock: Clock = Clock.systemUTC(),
) {

    private data class Attempts(val failures: Int, val lockedUntil: Instant?)

    private val attempts = ConcurrentHashMap<String, Attempts>()

    fun isLocked(username: String): Boolean {
        val entry = attempts[key(username)] ?: return false
        val lockedUntil = entry.lockedUntil ?: return false
        if (lockedUntil.isAfter(clock.instant())) return true
        attempts.remove(key(username))
        return false
    }

    fun recordFailure(username: String) {
        val entry = attempts.compute(key(username)) { _, current ->
            val failures = (current?.failures ?: 0) + 1
            val lockedUntil =
                if (failures >= MAX_FAILURES) clock.instant().plus(LOCK_DURATION) else current?.lockedUntil
            Attempts(failures, lockedUntil)
        }
        if (entry?.failures == MAX_FAILURES) {
            log.info(
                "Account '{}' locked for {} minutes after {} failed login attempts",
                username, LOCK_DURATION.toMinutes(), entry.failures,
            )
        }
    }

    fun recordSuccess(username: String) {
        attempts.remove(key(username))
    }

    private fun key(username: String) = username.lowercase()

    companion object {
        private val log = LoggerFactory.getLogger(LoginAttemptService::class.java)
        const val MAX_FAILURES = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}
