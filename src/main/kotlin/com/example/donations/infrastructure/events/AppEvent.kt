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

data class LoginSucceeded(val userid: String, val sourceIp: String) : AppEvent {
    override val name = "authn_login_success"
    override val level = Level.INFO
    override val fields = mapOf("userid" to userid, "sourceIp" to sourceIp)
}

data class LoginFailed(val userid: String, val sourceIp: String, val locked: Boolean) : AppEvent {
    override val name = "authn_login_fail"
    override val level = Level.WARN
    override val fields = mapOf("userid" to userid, "sourceIp" to sourceIp, "locked" to locked)
}

data class AccountLocked(val userid: String, val reason: String, val maxlimit: Int) : AppEvent {
    override val name = "authn_login_lock"
    override val level = Level.WARN
    override val fields = mapOf("userid" to userid, "reason" to reason, "maxlimit" to maxlimit)
}

data class PasswordChanged(val userid: String) : AppEvent {
    override val name = "authn_password_change"
    override val level = Level.INFO
    override val fields = mapOf("userid" to userid)
}

// OWASP marks this CRITICAL; SLF4J has no such level, so it maps to ERROR (ADR-005).
data class PasswordChangeFailed(val userid: String) : AppEvent {
    override val name = "authn_password_change_fail"
    override val level = Level.ERROR
    override val fields = mapOf("userid" to userid)
}
