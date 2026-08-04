package com.example.donations.infrastructure.events

import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * The only production class that holds a Logger (ADR-005). Events are written
 * through SLF4J's fluent API so each field stays a key-value pair rather than
 * being interpolated into the message.
 */
@Component
class EventLogger {

    private val log = LoggerFactory.getLogger("com.example.donations.events")

    fun emit(event: AppEvent) {
        var builder = log.atLevel(event.level).addKeyValue("event", event.name)
        event.fields.forEach { (key, value) -> builder = builder.addKeyValue(key, value) }
        actor()?.let { builder = builder.addKeyValue("actor", it) }
        builder.log(event.name)
    }

    private fun actor(): String? =
        SecurityContextHolder.getContext().authentication?.name?.takeIf { it != "anonymousUser" }
}
