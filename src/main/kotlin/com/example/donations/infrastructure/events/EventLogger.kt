package com.example.donations.infrastructure.events

import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * The only route by which application events are logged (ADR-005). Events are
 * written through SLF4J's fluent API so each field stays a key-value pair rather
 * than being interpolated into the message.
 *
 * GlobalExceptionHandler is the one other production class holding a Logger: it
 * keeps a raw stacktrace on unhandled exceptions, which no event can carry.
 */
@Component
class EventLogger {

    private val log = LoggerFactory.getLogger(LOGGER_NAME)

    fun emit(event: AppEvent) {
        var builder = log.atLevel(event.level).addKeyValue("event", event.name)
        event.fields.forEach { (key, value) -> builder = builder.addKeyValue(key, value) }
        actor()?.let { builder = builder.addKeyValue("actor", it) }
        builder.log(event.name)
    }

    private fun actor(): String? =
        SecurityContextHolder.getContext().authentication?.name?.takeIf { it != ANONYMOUS_PRINCIPAL }

    companion object {
        /** Every event goes to this one logger; tests attach to it by name. */
        const val LOGGER_NAME = "com.example.donations.events"

        /** Spring Security's principal name when no one is authenticated. */
        const val ANONYMOUS_PRINCIPAL = "anonymousUser"
    }
}
