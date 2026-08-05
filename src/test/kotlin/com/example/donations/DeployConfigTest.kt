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
import kotlin.test.assertNull

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

    @Autowired
    private lateinit var environment: Environment

    @Test
    @DisplayName("prod honors forwarded headers (Render edge terminates TLS)")
    fun prodUsesFrameworkForwardHeadersStrategy() {
        assertEquals(
            ServerProperties.ForwardHeadersStrategy.FRAMEWORK,
            serverProperties.forwardHeadersStrategy,
        )
    }

    /**
     * Events are only useful if something can read them: prod emits ECS JSON on
     * stdout so every field, including the MDC correlation id, stays a queryable
     * key instead of being flattened into a message string (ADR-005). ECS is a
     * published schema, so adopting a log platform later is this one property,
     * not reinstrumentation. The dev half of the split — the readable pattern —
     * is asserted in DeployPortBindingTest.
     */
    @Test
    @DisplayName("prod logs structured ECS JSON to the console")
    fun prodUsesEcsConsoleFormat() {
        assertEquals("ecs", environment.getProperty("logging.structured.format.console"))
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

    /**
     * The other half of the logging split asserted in DeployProdConfigTest:
     * outside prod the structured format stays unset, which is what leaves the
     * human-readable console pattern in place for local work. Asserted here
     * because this is the only context that boots without the prod profile and
     * already has an Environment; the dev document inherits this default, adding
     * nothing but CORS.
     */
    @Test
    @DisplayName("structured logging is off outside prod (readable console pattern)")
    fun consoleFormatUnsetOutsideProd() {
        assertNull(environment.getProperty("logging.structured.format.console"))
    }

    /**
     * The readable pattern prints message, logger and thread and drops both MDC
     * and key-values, so without this the correlation id is invisible in dev —
     * exactly where the "find the id, pull every event from that request"
     * workflow is first used — and an event shows its name but not its fields.
     * The :- default keeps startup and shutdown lines, logged outside any
     * request, from rendering a stray marker. %kvp is deliberately carried in
     * the correlation slot: the tidy alternative is owning a full copy of Boot's
     * console pattern across upgrades, which costs more than it returns.
     */
    @Test
    @DisplayName("correlation pattern surfaces the request id in the readable output")
    fun correlationPatternIncludesRequestId() {
        assertEquals("[%X{requestId:-}] %kvp ", environment.getProperty("logging.pattern.correlation"))
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
