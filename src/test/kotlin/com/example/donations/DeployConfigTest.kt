package com.example.donations

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.autoconfigure.ServerProperties
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

/**
 * Guards the Render deploy configuration (plans/deploy-render.md):
 * the prod profile must honor X-Forwarded-* headers so the Secure session
 * cookie works behind Render's TLS-terminating proxy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration::class)
@DisplayName("Prod deploy config")
class DeployProdConfigTest {

    @Autowired
    private lateinit var serverProperties: ServerProperties

    @Test
    @DisplayName("prod honors forwarded headers (Render edge terminates TLS)")
    fun prodUsesFrameworkForwardHeadersStrategy() {
        assertEquals(
            ServerProperties.ForwardHeadersStrategy.FRAMEWORK,
            serverProperties.forwardHeadersStrategy,
        )
    }
}

/**
 * The server port must bind from the PORT env var that Render injects,
 * falling back to 8081 locally: server.port = ${PORT:8081}. Asserting the
 * exact name PORT (not SERVER_PORT) is what makes the Render deploy reachable.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["PORT=12345"],
)
@Import(TestcontainersConfiguration::class)
@DisplayName("PORT binding")
class DeployPortBindingTest {

    @Autowired
    private lateinit var environment: Environment

    @Test
    @DisplayName("server.port resolves from the PORT property")
    fun serverPortBindsFromPortVar() {
        assertEquals("12345", environment.getProperty("server.port"))
    }
}
