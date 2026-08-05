package com.example.donations

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.donations.infrastructure.events.EventLogger
import com.example.donations.user.LoginAttemptService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Captures what actually reaches the log rather than what a collaborator was
 * asked to do, so level and key-value mapping are exercised too.
 */
@DisplayName("Authentication Event Emission Tests")
class AuthEventTest {

    private val eventsLogger = LoggerFactory.getLogger(EventLogger.LOGGER_NAME) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private val service = LoginAttemptService(EventLogger())

    @BeforeEach
    fun attachAppender() {
        appender.start()
        eventsLogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        eventsLogger.detachAppender(appender)
        appender.stop()
    }

    private fun emitted(name: String) = appender.list.filter { it.message == name }

    private fun fieldsOf(event: ILoggingEvent) = event.keyValuePairs.associate { it.key to it.value }

    @Test
    @DisplayName("Failures below the threshold emit no lock event")
    fun noLockBelowThreshold() {
        repeat(LoginAttemptService.MAX_FAILURES - 1) { service.recordFailure("admin") }
        assertTrue(emitted("authn_login_lock").isEmpty())
    }

    @Test
    @DisplayName("Reaching the threshold emits one lock event with reason and limit")
    fun lockEmittedAtThreshold() {
        repeat(LoginAttemptService.MAX_FAILURES) { service.recordFailure("admin") }

        val locks = emitted("authn_login_lock")
        assertEquals(1, locks.size)
        assertEquals(Level.WARN, locks.single().level)
        assertEquals(
            mapOf(
                "event" to "authn_login_lock",
                "userid" to "admin",
                "reason" to "maxretries",
                "maxlimit" to LoginAttemptService.MAX_FAILURES,
            ),
            fieldsOf(locks.single()),
        )
    }

    @Test
    @DisplayName("Failures after the lock do not emit a second lock event")
    fun lockNotRepeatedAfterThreshold() {
        repeat(LoginAttemptService.MAX_FAILURES + 3) { service.recordFailure("admin") }
        assertEquals(1, emitted("authn_login_lock").size)
    }

    @Test
    @DisplayName("authn_login_fail_max is never emitted")
    fun loginFailMaxNeverEmitted() {
        repeat(LoginAttemptService.MAX_FAILURES + 3) { service.recordFailure("admin") }
        assertTrue(appender.list.none { it.message == "authn_login_fail_max" })
    }
}
