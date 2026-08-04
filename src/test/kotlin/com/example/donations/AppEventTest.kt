package com.example.donations

import com.example.donations.infrastructure.events.AccountLocked
import com.example.donations.infrastructure.events.LoginFailed
import com.example.donations.infrastructure.events.LoginSucceeded
import com.example.donations.infrastructure.events.PasswordChangeFailed
import com.example.donations.infrastructure.events.PasswordChanged
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import kotlin.test.assertEquals

@DisplayName("App Event Tests")
class AppEventTest {

    @Test
    @DisplayName("Login success uses the OWASP event name")
    fun loginSucceededName() {
        assertEquals("authn_login_success", LoginSucceeded("admin", "10.0.0.1").name)
    }

    @Test
    @DisplayName("Login success is logged at INFO")
    fun loginSucceededLevel() {
        assertEquals(Level.INFO, LoginSucceeded("admin", "10.0.0.1").level)
    }

    @Test
    @DisplayName("Login success carries the userid and source IP and nothing else")
    fun loginSucceededFields() {
        assertEquals(
            mapOf("userid" to "admin", "sourceIp" to "10.0.0.1"),
            LoginSucceeded("admin", "10.0.0.1").fields,
        )
    }

    @Test
    @DisplayName("Login failure uses the OWASP event name")
    fun loginFailedName() {
        assertEquals("authn_login_fail", LoginFailed("admin", "10.0.0.1", locked = false).name)
    }

    @Test
    @DisplayName("Login failure is logged at WARN")
    fun loginFailedLevel() {
        assertEquals(Level.WARN, LoginFailed("admin", "10.0.0.1", locked = false).level)
    }

    @Test
    @DisplayName("Login failure carries the userid, source IP and locked flag")
    fun loginFailedFields() {
        assertEquals(
            mapOf("userid" to "admin", "sourceIp" to "10.0.0.1", "locked" to true),
            LoginFailed("admin", "10.0.0.1", locked = true).fields,
        )
    }

    @Test
    @DisplayName("Account lock uses the OWASP event name")
    fun accountLockedName() {
        assertEquals("authn_login_lock", AccountLocked("admin", "maxretries", 5).name)
    }

    @Test
    @DisplayName("Account lock is logged at WARN")
    fun accountLockedLevel() {
        assertEquals(Level.WARN, AccountLocked("admin", "maxretries", 5).level)
    }

    @Test
    @DisplayName("Account lock carries the userid, reason and limit")
    fun accountLockedFields() {
        assertEquals(
            mapOf("userid" to "admin", "reason" to "maxretries", "maxlimit" to 5),
            AccountLocked("admin", "maxretries", 5).fields,
        )
    }

    @Test
    @DisplayName("Password change uses the OWASP event name")
    fun passwordChangedName() {
        assertEquals("authn_password_change", PasswordChanged("admin").name)
    }

    @Test
    @DisplayName("Password change is logged at INFO")
    fun passwordChangedLevel() {
        assertEquals(Level.INFO, PasswordChanged("admin").level)
    }

    @Test
    @DisplayName("Password change carries the userid and nothing else")
    fun passwordChangedFields() {
        assertEquals(mapOf("userid" to "admin"), PasswordChanged("admin").fields)
    }

    @Test
    @DisplayName("Password change failure uses the OWASP event name")
    fun passwordChangeFailedName() {
        assertEquals("authn_password_change_fail", PasswordChangeFailed("admin").name)
    }

    @Test
    @DisplayName("Password change failure is logged at ERROR (OWASP CRITICAL has no SLF4J level)")
    fun passwordChangeFailedLevel() {
        assertEquals(Level.ERROR, PasswordChangeFailed("admin").level)
    }

    @Test
    @DisplayName("Password change failure carries the userid and nothing else")
    fun passwordChangeFailedFields() {
        assertEquals(mapOf("userid" to "admin"), PasswordChangeFailed("admin").fields)
    }
}
