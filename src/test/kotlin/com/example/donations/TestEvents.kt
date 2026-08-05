package com.example.donations

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.donations.expense.ExpenseCategory
import com.example.donations.infrastructure.events.AccountLocked
import com.example.donations.infrastructure.events.AdminAction
import com.example.donations.infrastructure.events.AdminActionType
import com.example.donations.infrastructure.events.AppEvent
import com.example.donations.infrastructure.events.AuthorizationChanged
import com.example.donations.infrastructure.events.AuthorizationFailed
import com.example.donations.infrastructure.events.DonationCreated
import com.example.donations.infrastructure.events.DonationUpdated
import com.example.donations.infrastructure.events.DonorCreated
import com.example.donations.infrastructure.events.DonorUpdated
import com.example.donations.infrastructure.events.EventLogger
import com.example.donations.infrastructure.events.ExpenseCreated
import com.example.donations.infrastructure.events.ExpenseUpdated
import com.example.donations.infrastructure.events.LoginFailed
import com.example.donations.infrastructure.events.LoginSucceeded
import com.example.donations.infrastructure.events.PasswordChangeFailed
import com.example.donations.infrastructure.events.PasswordChanged
import com.example.donations.infrastructure.events.UnexpectedError
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * Captures what EventLogger actually writes during [capture], so tests assert on
 * real emissions — level and key-value mapping included — rather than on a stub.
 * The appender is always detached, so captures cannot leak between tests.
 */
object TestEvents {

    /**
     * One instance of every event type. AppEventPiiGuardTest asserts this covers
     * the sealed hierarchy, so adding an event forces this list to grow and the
     * new type is dragged through both the guards and the real formatter.
     */
    val samples: List<AppEvent> = listOf(
        LoginSucceeded("admin", "127.0.0.1"),
        LoginFailed("admin", "127.0.0.1", locked = true),
        AccountLocked("admin", "maxretries", 5),
        PasswordChanged("admin"),
        PasswordChangeFailed("admin"),
        AuthorizationFailed("operator1", "/api/v1/users"),
        AdminAction("admin", AdminActionType.USER_CREATE, 42),
        AuthorizationChanged("operator1", "OPERATOR", "ADMIN", 42),
        // donorId null: an anonymous donation, the only null field value in the
        // vocabulary. Other events already cover non-null Long rendering.
        DonationCreated(1, null, BigDecimal("100.00")),
        DonationUpdated(1),
        DonorCreated(7),
        DonorUpdated(7),
        ExpenseCreated(3, BigDecimal("40.00"), ExpenseCategory.SUPPLIES),
        ExpenseUpdated(3),
        UnexpectedError("java.lang.IllegalStateException", "/api/v1/donors"),
    )

    fun capture(block: () -> Unit): List<ILoggingEvent> {
        val eventsLogger = LoggerFactory.getLogger(EventLogger.LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        eventsLogger.addAppender(appender)
        try {
            block()
        } finally {
            eventsLogger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.toList()
    }

    fun fieldsOf(event: ILoggingEvent): Map<String, Any?> =
        event.keyValuePairs.associate { it.key to it.value }
}
