package com.example.donations.infrastructure.events

import org.slf4j.event.Level

/**
 * The closed set of events this application emits, named with the OWASP
 * Application Logging Vocabulary (ADR-005). Each event declares its own fields,
 * so donor personal data has no field to travel in.
 */
sealed interface AppEvent {
    val name: String
    val level: Level
    val fields: Map<String, Any?>
}

data class LoginSucceeded(val userid: String) : AppEvent {
    override val name = "authn_login_success"
    override val level = Level.INFO
    override val fields = mapOf("userid" to userid)
}
