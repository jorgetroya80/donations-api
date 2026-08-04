package com.example.donations

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * Captures what EventLogger actually writes during [capture], so tests assert on
 * real emissions — level and key-value mapping included — rather than on a stub.
 * The appender is always detached, so captures cannot leak between tests.
 */
object TestEvents {

    fun capture(block: () -> Unit): List<ILoggingEvent> {
        val eventsLogger = LoggerFactory.getLogger("com.example.donations.events") as Logger
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
