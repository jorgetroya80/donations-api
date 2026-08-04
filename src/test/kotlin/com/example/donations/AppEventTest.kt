package com.example.donations

import com.example.donations.infrastructure.events.LoginSucceeded
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import kotlin.test.assertEquals

@DisplayName("App Event Tests")
class AppEventTest {

    @Test
    @DisplayName("Login success uses the OWASP event name")
    fun loginSucceededName() {
        assertEquals("authn_login_success", LoginSucceeded("admin").name)
    }

    @Test
    @DisplayName("Login success is logged at INFO")
    fun loginSucceededLevel() {
        assertEquals(Level.INFO, LoginSucceeded("admin").level)
    }

    @Test
    @DisplayName("Login success carries the userid and nothing else")
    fun loginSucceededFields() {
        assertEquals(mapOf("userid" to "admin"), LoginSucceeded("admin").fields)
    }
}
