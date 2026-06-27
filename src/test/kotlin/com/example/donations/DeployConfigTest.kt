package com.example.donations

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.autoconfigure.ServerProperties
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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

/**
 * Render's health check hits /actuator/health/liveness. It must be reachable
 * without auth and report only livenessState (not the DB), so a Neon cold start
 * does not make the check 503 and bounce the service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@DisplayName("Liveness probe")
class DeployLivenessTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("liveness probe is reachable anonymously and UP")
    fun livenessReachableAnonymously() {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
    }
}
