package com.example.donations

import com.example.donations.infrastructure.events.EventLogger
import com.example.donations.infrastructure.events.RequestIdFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders every event through the real ECS encoder, which no other test does:
 * ListAppender captures events *before* formatting, so a formatter rejection —
 * a duplicate key, a value that cannot be written — is invisible to the rest of
 * the suite and silently drops the line in prod. Logback swallows the failure,
 * so the "no appender error" assertion below is as important as the JSON itself.
 *
 * The sample list lives in TestEvents; AppEventPiiGuardTest asserts it covers
 * every event type.
 */
@SpringBootTest(properties = ["logging.structured.format.console=ecs"])
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
@DisplayName("Event Formatter Tests")
class EventFormatterTest {

    @Autowired
    private lateinit var eventLogger: EventLogger

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    /**
     * Production emits inside an authenticated request, so both the MDC
     * correlation id and the "actor" key are present. Without them here, a field
     * colliding with either would render cleanly and the collision would reach
     * production unnoticed — which is exactly how the requestId defect escaped.
     */
    @BeforeEach
    fun enterRequestContext() {
        MDC.put(RequestIdFilter.REQUEST_ID, "formatter-test-request-id")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("formatter-test-actor", null, emptyList())
    }

    @AfterEach
    fun leaveRequestContext() {
        MDC.remove(RequestIdFilter.REQUEST_ID)
        SecurityContextHolder.clearContext()
    }

    @Test
    @DisplayName("Every event renders as valid ECS JSON carrying its declared fields")
    fun everyEventRendersAsValidEcsJson(output: CapturedOutput) {
        TestEvents.samples.forEach { eventLogger.emit(it) }

        val rendered = output.all.lineSequence()
            .filter { it.startsWith("{") && """"logger":"${EventLogger.LOGGER_NAME}"""" in it }
            .map { objectMapper.readValue(it, Map::class.java) }
            .associateBy { it["message"] }

        TestEvents.samples.forEach { sample ->
            val line = rendered[sample.name] ?: throw AssertionError(
                "No rendered line for '${sample.name}'. Rendered: ${rendered.keys}",
            )
            assertEquals(sample.name, line["event"], "event key for ${sample.name}")
            assertEquals(sample.level.name, (line["log"] as Map<*, *>)["level"], "level for ${sample.name}")
            assertEquals(
                "formatter-test-request-id",
                line[RequestIdFilter.REQUEST_ID],
                "correlation id for ${sample.name}",
            )
            assertEquals("formatter-test-actor", line["actor"], "actor for ${sample.name}")
            sample.fields.keys.forEach { field ->
                assertTrue(line.containsKey(field), "${sample.name} is missing field '$field'")
            }
        }
    }

    /**
     * Logback reports an encoder failure on its own status stream and drops the
     * line; nothing throws. Without this, the test above could pass simply by
     * finding fewer lines than it expected.
     */
    @Test
    @DisplayName("Rendering produces no appender failure")
    fun renderingProducesNoAppenderFailure(output: CapturedOutput) {
        TestEvents.samples.forEach { eventLogger.emit(it) }

        assertTrue(
            "failed to append" !in output.all,
            "Logback rejected an event: ${output.all.lines().filter { "failed to append" in it }}",
        )
        assertTrue(
            "ERROR in ch.qos.logback" !in output.all,
            "Logback reported an internal error: ${output.all.lines().filter { "ERROR in ch.qos.logback" in it }}",
        )
    }
}
